package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.TechnicianLevel;
import java.util.List;
import java.util.Map;
public interface TechnicianLevelService extends IService<TechnicianLevel> {

    /**
     * 获取所有启用的等级
     */
    List<TechnicianLevel> getActiveLevels();

    /**
     * 获取下一等级
     */
    TechnicianLevel getNextLevel(Integer currentSortOrder);

    /**
     * 统计每个等级的技师数量
     */
    List<Map<String, Object>> countEmployeesByLevel();

    /**
     * 启停等级
     */
    boolean toggleStatus(Long id, Integer status);

    /**
     * M08 晋升评估：评估指定员工是否满足晋升到下一等级的条件
     *
     * @param employeeId 员工ID
     * @return 评估结果（含各条件达标情况 + 建议）
     */
    Map<String, Object> evaluatePromotion(Long employeeId);

    /**
     * M08 执行晋升：将员工晋升到下一等级
     *
     * @param employeeId 员工ID
     * @param operatorId 操作人ID（可选）
     * @param note 备注（可选）
     * @return 晋升结果
     */
    Map<String, Object> promote(Long employeeId, Long operatorId, String note);
}
