package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.StyleLibrary;
import java.util.List;
import java.util.Map;
public interface StyleLibraryService extends IService<StyleLibrary> {
    List<StyleLibrary> queryList(String styleType, String styleTag, Integer difficultyLevel,
                                 Integer status, Integer isNew, Integer isHot);
    List<StyleLibrary> findHotStyles();
    List<Map<String, Object>> statisticsByType(Long storeId);
    /**
     * 记录浏览
     */
    void recordView(Long styleId);
    /**
     * 记录收藏
     */
    void recordFavorite(Long styleId);
    /**
     * AI 推荐款式
     * 基于客户偏好、浏览历史、订单历史等智能推荐
     */
    List<StyleLibrary> recommend(Long memberId, String styleType, int limit);
    /**
     * 切换点赞（赞/取消赞），返回当前点赞数
     */
    int toggleLike(Long styleId);
    /**
     * 快速下单（生成预约链接）
     * @return 包含 orderUrl 的 Map
     */
    Map<String, Object> quickOrder(Long styleId, Long memberId, Long technicianId, Long storeId);
    /**
     * P2-12 IND-12：完整快速下单
     *
     * <p>请求 body（Map）：
     * <pre>
     * {
     *   "styleId": 1,
     *   "memberId": 10,
     *   "technicianId": 5,       // 可选
     *   "storeId": 3,             // 可选
     *   "bookingDate": "2026-09-10",
     *   "startTime": "14:00",     // HH:mm
     *   "remark": "加急"
     * }
     * </pre>
     *
     * <p>返回 Map 含：bookingId / bookingNo / styleId / styleName /
     *   technicianName / bookingDate / startTime / endTime / status /
     *   materials(List) / message
     */
    Map<String, Object> quickOrderFull(Map<String, Object> body);
    /**
     * 初始化默认款式数据（幂等）
     * @return createdCount 本次新建数量
     */
    int initDefault();
}
