package com.meiyetec.dcep.ota;

import com.meiyetec.entity.DcepFirmwarePackage;
import com.meiyetec.entity.DcepFirmwareUpgradeTask;
import com.meiyetec.mapper.DcepFirmwarePackageMapper;
import com.meiyetec.mapper.DcepFirmwareUpgradeTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DCEP-04 阶段 C：固件管理服务
 *
 * <p>职责：
 * <ol>
 *   <li>查询可用升级包（按设备型号匹配）</li>
 *   <li>创建升级任务</li>
 *   <li>记录升级进度</li>
 *   <li>支持断点续传</li>
 * </ol>
 */
@Slf4j
@Service
public class FirmwareService {

    @Autowired private DcepFirmwarePackageMapper packageMapper;
    @Autowired private DcepFirmwareUpgradeTaskMapper taskMapper;

    /**
     * 查询可用升级（比当前 fwVersion 更高的 released 包）
     */
    public FirmwareUpdateInfo findAvailableUpdate(String deviceModel, String currentFwVersion) {
        if (deviceModel == null || currentFwVersion == null) return null;
        // 简化实现：生产环境用 SQL 模糊匹配
        List<DcepFirmwarePackage> packages = packageMapper.selectList(null);
        for (DcepFirmwarePackage pkg : packages) {
            if (!"released".equals(pkg.getReleaseStatus())) continue;
            if (!deviceModel.equals(pkg.getDeviceModel())) continue;
            if (compareSemver(pkg.getVersion(), currentFwVersion) <= 0) continue;
            return FirmwareUpdateInfo.builder()
                    .packageId(pkg.getId())
                    .version(pkg.getVersion())
                    .filePath(pkg.getFilePath())
                    .sha256(pkg.getSha256())
                    .fileSize(pkg.getFileSize())
                    .signature(pkg.getSignature())
                    .signAlgorithm(pkg.getSignAlgorithm())
                    .mandatory(pkg.getMandatory() != null && pkg.getMandatory() == 1)
                    .build();
        }
        return null;
    }

    /**
     * 创建升级任务
     */
    public DcepFirmwareUpgradeTask createTask(Long packageId, Long deviceId, String deviceSerial,
                                                Long tenantId, boolean scheduled) {
        DcepFirmwareUpgradeTask task = new DcepFirmwareUpgradeTask();
        task.setTenantId(tenantId);
        task.setPackageId(packageId);
        task.setDeviceId(deviceId);
        task.setDeviceSerial(deviceSerial);
        task.setStatus("pending");
        task.setDownloadedBytes(0L);
        task.setProgressPercent(0);
        task.setRetryCount(0);
        if (scheduled) {
            task.setScheduledAt(LocalDateTime.now());
        } else {
            task.setScheduledAt(LocalDateTime.now());
            task.setStatus("downloading");
            task.setStartedAt(LocalDateTime.now());
        }
        taskMapper.insert(task);
        return task;
    }

    /**
     * 断点续传：客户端上报已下载字节
     */
    public DcepFirmwareUpgradeTask reportProgress(Long taskId, Long downloadedBytes,
                                                    Integer progressPercent) {
        DcepFirmwareUpgradeTask task = taskMapper.selectById(taskId);
        if (task == null) return null;
        task.setDownloadedBytes(downloadedBytes);
        task.setProgressPercent(progressPercent);
        if (progressPercent != null && progressPercent >= 100) {
            task.setStatus("verifying");
        }
        taskMapper.updateById(task);
        return task;
    }

    /**
     * 标记完成（设备端确认升级成功）
     */
    public DcepFirmwareUpgradeTask markCompleted(Long taskId, boolean success, String errorCode,
                                                   String errorMessage) {
        DcepFirmwareUpgradeTask task = taskMapper.selectById(taskId);
        if (task == null) return null;
        task.setStatus(success ? "success" : "failed");
        task.setProgressPercent(success ? 100 : task.getProgressPercent());
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    /** 简单的 semver 比较：1.0.0 < 1.1.0 < 2.0.0 */
    private int compareSemver(String a, String b) {
        if (a == null || b == null) return 0;
        try {
            String[] ap = a.split("\\.");
            String[] bp = b.split("\\.");
            for (int i = 0; i < Math.max(ap.length, bp.length); i++) {
                int av = i < ap.length ? Integer.parseInt(ap[i]) : 0;
                int bv = i < bp.length ? Integer.parseInt(bp[i]) : 0;
                if (av != bv) return Integer.compare(av, bv);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
