package com.meiyetec.service;

import com.meiyetec.entity.CareRecipe;
import com.meiyetec.entity.CareRecipeItem;
import com.meiyetec.entity.CareSkinAssessment;
import com.meiyetec.entity.CareSuggestionLog;
import com.meiyetec.entity.CareSuggestionRule;
import com.meiyetec.entity.CareSuggestionTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0.2 CARE — 实体字段/POJO 完整性测试（不加载 Spring 上下文，避免项目级 ambiguous mapping 影响）
 */
class CareEntityFieldsTest {

    @Test
    void care_recipe_has_tenantId() {
        Field f = findField(CareRecipe.class, "tenantId");
        assertNotNull(f, "CareRecipe.tenantId 字段必须存在");
        assertEquals(Long.class, f.getType());
    }

    @Test
    void care_recipe_item_has_recipeId() {
        Field f = findField(CareRecipeItem.class, "recipeId");
        assertNotNull(f, "CareRecipeItem.recipeId 字段必须存在");
        assertEquals(Long.class, f.getType());
    }

    @Test
    void care_skin_assessment_has_tenantId_memberId() {
        Field tid = findField(CareSkinAssessment.class, "tenantId");
        Field mid = findField(CareSkinAssessment.class, "memberId");
        assertNotNull(tid, "CareSkinAssessment.tenantId 必须存在");
        assertNotNull(mid, "CareSkinAssessment.memberId 必须存在");
        assertEquals(Long.class, tid.getType());
        assertEquals(Long.class, mid.getType());
    }

    @Test
    void care_suggestion_rule_log_template_have_tenantId() {
        assertNotNull(findField(CareSuggestionRule.class, "tenantId"));
        assertNotNull(findField(CareSuggestionLog.class, "tenantId"));
        assertNotNull(findField(CareSuggestionTemplate.class, "tenantId"));
    }

    @Test
    void care_recipe_pojo_setter_getter_ok() {
        CareRecipe r = new CareRecipe();
        r.setTenantId(7L);
        r.setRecipeCode("X");
        r.setRecipeName("name");
        r.setSkinType("OILY");
        r.setStatus("ACTIVE");
        r.setScore(85);
        assertEquals(7L, r.getTenantId());
        assertEquals("OILY", r.getSkinType());
        assertEquals(85, r.getScore());
    }

    @Test
    void care_skin_assessment_pojo_ok() {
        CareSkinAssessment a = new CareSkinAssessment();
        a.setTenantId(1L);
        a.setMemberId(123L);
        a.setSkinType("DRY");
        a.setMoistureLevel(3);
        a.setPigmentLevel(6);
        a.setWrinkleLevel(5);
        a.setRecommendedRecipeId(99L);
        assertEquals(123L, a.getMemberId());
        assertEquals("DRY", a.getSkinType());
        assertEquals(99L, a.getRecommendedRecipeId());
    }

    private Field findField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(name)) return f;
        }
        return null;
    }
}