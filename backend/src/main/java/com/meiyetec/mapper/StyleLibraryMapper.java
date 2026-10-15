package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.StyleLibrary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface StyleLibraryMapper extends BaseMapper<StyleLibrary> {

    @Select("<script>"
            + "SELECT * FROM biz_style_library WHERE deleted = 0"
            + "<if test='styleType != null'> AND style_type = #{styleType}</if>"
            + "<if test='styleTag != null'> AND style_tag LIKE CONCAT('%', #{styleTag}, '%')</if>"
            + "<if test='difficultyLevel != null'> AND difficulty_level = #{difficultyLevel}</if>"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "<if test='isNew != null'> AND is_new = #{isNew}</if>"
            + "<if test='isHot != null'> AND is_hot = #{isHot}</if>"
            + " ORDER BY is_hot DESC, is_new DESC, view_count DESC"
            + "</script>")
    List<StyleLibrary> queryList(@Param("styleType") String styleType,
                                @Param("styleTag") String styleTag,
                                @Param("difficultyLevel") Integer difficultyLevel,
                                @Param("status") Integer status,
                                @Param("isNew") Integer isNew,
                                @Param("isHot") Integer isHot);

    @Select("SELECT * FROM biz_style_library WHERE deleted = 0 AND status = 1 "
            + "ORDER BY use_count DESC LIMIT 10")
    List<StyleLibrary> findHotStyles();

    @Select("SELECT style_type AS styleType, COUNT(*) AS count FROM biz_style_library "
            + "WHERE deleted = 0 AND status = 1 GROUP BY style_type")
    List<Map<String, Object>> statisticsByType(@Param("storeId") Long storeId);

    @Update("UPDATE biz_style_library SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    void incrementViewCount(@Param("id") Long id);

    @Update("UPDATE biz_style_library SET favorite_count = favorite_count + #{delta} WHERE id = #{id} AND deleted = 0")
    void incrementLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE biz_style_library SET favorite_count = favorite_count + 1 WHERE id = #{id} AND deleted = 0")
    void incrementFavoriteCount(@Param("id") Long id);
}
