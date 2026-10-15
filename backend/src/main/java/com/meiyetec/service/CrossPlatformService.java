package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CrossPlatform;
import com.meiyetec.entity.CrossPlatformCapability;
import com.meiyetec.entity.CrossPlatformSdk;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CrossPlatformCapabilityMapper;
import com.meiyetec.mapper.CrossPlatformMapper;
import com.meiyetec.mapper.CrossPlatformSdkMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.9：跨端平台管理
 *
 * <p>提供：
 * <ul>
 *   <li>平台定义（WEB / H5 / MP_WEIXIN / MP_ALIPAY / APP_IOS / APP_ANDROID / PAD）</li>
 *   <li>端能力探测（camera / gps / biometric / push / nfc / ble）</li>
 *   <li>SDK 版本管理（强制升级 / 灰度）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/cross")
@RequiredArgsConstructor
public class CrossPlatformService {

    private final CrossPlatformMapper platformMapper;
    private final CrossPlatformSdkMapper sdkMapper;
    private final CrossPlatformCapabilityMapper capabilityMapper;

    // ====================================================================
    // 1. 平台管理
    // ====================================================================

    @PostMapping("/platform")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrossPlatform> upsertPlatform(@RequestBody CrossPlatform p) {
        if (p.getPlatformCode() == null) return Result.error(BizCode.BAD_REQUEST, "platformCode 必填");
        CrossPlatform exist = platformMapper.selectOne(
                new LambdaQueryWrapper<CrossPlatform>().eq(CrossPlatform::getPlatformCode, p.getPlatformCode()));
        if (exist != null) {
            exist.setPlatformName(p.getPlatformName());
            exist.setPlatformType(p.getPlatformType());
            exist.setFramework(p.getFramework());
            exist.setMinVersion(p.getMinVersion());
            exist.setSdkVersion(p.getSdkVersion());
            exist.setEnabled(p.getEnabled());
            exist.setIconUrl(p.getIconUrl());
            platformMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (p.getPlatformType() == null) p.setPlatformType("WEB");
        if (p.getEnabled() == null) p.setEnabled(1);
        platformMapper.insert(p);
        return Result.success("已创建", p);
    }

    @GetMapping("/platform/list")
    public Result<List<CrossPlatform>> listPlatforms(@RequestParam(required = false) String platformType,
                                                       @RequestParam(required = false) Integer enabled) {
        LambdaQueryWrapper<CrossPlatform> qw = new LambdaQueryWrapper<>();
        if (platformType != null) qw.eq(CrossPlatform::getPlatformType, platformType);
        if (enabled != null) qw.eq(CrossPlatform::getEnabled, enabled);
        qw.orderByAsc(CrossPlatform::getPlatformCode);
        return Result.success(platformMapper.selectList(qw));
    }

    @GetMapping("/platform/{code}")
    public Result<CrossPlatform> getPlatform(@PathVariable String code) {
        return Result.success(platformMapper.selectOne(
                new LambdaQueryWrapper<CrossPlatform>().eq(CrossPlatform::getPlatformCode, code)));
    }

    // ====================================================================
    // 2. 端能力
    // ====================================================================

    @PostMapping("/capability")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrossPlatformCapability> upsertCapability(@RequestBody CrossPlatformCapability c) {
        if (c.getPlatformCode() == null || c.getCapabilityKey() == null)
            return Result.error(BizCode.BAD_REQUEST, "platformCode / capabilityKey 必填");
        if (c.getSupported() == null) c.setSupported(1);
        CrossPlatformCapability exist = capabilityMapper.selectOne(
                new LambdaQueryWrapper<CrossPlatformCapability>()
                        .eq(CrossPlatformCapability::getPlatformCode, c.getPlatformCode())
                        .eq(CrossPlatformCapability::getCapabilityKey, c.getCapabilityKey()));
        if (exist != null) {
            exist.setCapabilityName(c.getCapabilityName());
            exist.setSupported(c.getSupported());
            exist.setVersion(c.getVersion());
            exist.setExtraConfig(c.getExtraConfig());
            capabilityMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        capabilityMapper.insert(c);
        return Result.success("已创建", c);
    }

    /**
     * 端能力探测：客户端上报
     */
    @PostMapping("/capability/probe")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<CrossPlatformCapability>> probeCapability(@RequestBody Map<String, Object> body) {
        String platformCode = (String) body.get("platformCode");
        if (platformCode == null) return Result.error(BizCode.BAD_REQUEST, "platformCode 必填");
        @SuppressWarnings("unchecked")
        Map<String, Object> caps = (Map<String, Object>) body.getOrDefault("capabilities", new HashMap<>());
        for (Map.Entry<String, Object> e : caps.entrySet()) {
            CrossPlatformCapability cap = new CrossPlatformCapability();
            cap.setPlatformCode(platformCode);
            cap.setCapabilityKey(e.getKey());
            cap.setSupported("true".equals(String.valueOf(e.getValue())) || Boolean.TRUE.equals(e.getValue()) ? 1 : 0);
            upsertCapability(cap);
        }
        return Result.success("已探测", capabilityMapper.selectList(
                new LambdaQueryWrapper<CrossPlatformCapability>().eq(CrossPlatformCapability::getPlatformCode, platformCode)));
    }

    @GetMapping("/capability/{platformCode}")
    public Result<List<CrossPlatformCapability>> listCapabilities(@PathVariable String platformCode) {
        return Result.success(capabilityMapper.selectList(
                new LambdaQueryWrapper<CrossPlatformCapability>().eq(CrossPlatformCapability::getPlatformCode, platformCode)));
    }

    @GetMapping("/capability/{platformCode}/{key}/check")
    public Result<Map<String, Object>> checkCapability(@PathVariable String platformCode,
                                                          @PathVariable String key) {
        CrossPlatformCapability cap = capabilityMapper.selectOne(
                new LambdaQueryWrapper<CrossPlatformCapability>()
                        .eq(CrossPlatformCapability::getPlatformCode, platformCode)
                        .eq(CrossPlatformCapability::getCapabilityKey, key));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("platformCode", platformCode);
        data.put("capabilityKey", key);
        data.put("supported", cap != null && cap.getSupported() != null && cap.getSupported() == 1);
        return Result.success(data);
    }

    // ====================================================================
    // 3. SDK 版本管理
    // ====================================================================

    @PostMapping("/sdk")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrossPlatformSdk> upsertSdk(@RequestBody CrossPlatformSdk s) {
        if (s.getPlatformCode() == null || s.getSdkVersion() == null)
            return Result.error(BizCode.BAD_REQUEST, "platformCode / sdkVersion 必填");
        CrossPlatformSdk exist = sdkMapper.selectOne(
                new LambdaQueryWrapper<CrossPlatformSdk>()
                        .eq(CrossPlatformSdk::getPlatformCode, s.getPlatformCode())
                        .eq(CrossPlatformSdk::getSdkVersion, s.getSdkVersion()));
        if (exist != null) {
            exist.setDownloadUrl(s.getDownloadUrl());
            exist.setFileHash(s.getFileHash());
            exist.setFileSize(s.getFileSize());
            exist.setChangelog(s.getChangelog());
            exist.setForceUpgrade(s.getForceUpgrade());
            exist.setMinClient(s.getMinClient());
            exist.setDeprecated(s.getDeprecated());
            sdkMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (s.getForceUpgrade() == null) s.setForceUpgrade(0);
        if (s.getDeprecated() == null) s.setDeprecated(0);
        if (s.getReleasedAt() == null) s.setReleasedAt(LocalDateTime.now());
        sdkMapper.insert(s);
        return Result.success("已发布", s);
    }

    /**
     * 客户端升级检查
     */
    @PostMapping("/sdk/check-upgrade")
    public Result<Map<String, Object>> checkUpgrade(@RequestBody Map<String, Object> body) {
        String platformCode = (String) body.get("platformCode");
        String currentVersion = (String) body.get("currentVersion");
        if (platformCode == null) return Result.error(BizCode.BAD_REQUEST, "platformCode 必填");

        List<CrossPlatformSdk> sdks = sdkMapper.selectList(
                new LambdaQueryWrapper<CrossPlatformSdk>()
                        .eq(CrossPlatformSdk::getPlatformCode, platformCode)
                        .eq(CrossPlatformSdk::getDeprecated, 0)
                        .orderByDesc(CrossPlatformSdk::getReleasedAt));
        CrossPlatformSdk latest = sdks.isEmpty() ? null : sdks.get(0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("latestVersion", latest == null ? null : latest.getSdkVersion());
        data.put("currentVersion", currentVersion);
        boolean upgrade = latest != null && currentVersion != null
                && compareVersion(latest.getSdkVersion(), currentVersion) > 0;
        data.put("upgradeAvailable", upgrade);
        data.put("forceUpgrade", upgrade && latest.getForceUpgrade() != null && latest.getForceUpgrade() == 1);
        data.put("downloadUrl", upgrade ? latest.getDownloadUrl() : null);
        data.put("fileHash", upgrade ? latest.getFileHash() : null);
        return Result.success(data);
    }

    /**
     * 版本号比较：1.2.3 vs 1.2.4
     */
    public static int compareVersion(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < a.length ? Integer.parseInt(a[i]) : 0;
            int n2 = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }
}