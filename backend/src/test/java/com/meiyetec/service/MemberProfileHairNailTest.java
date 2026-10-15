package com.meiyetec.service;

import com.meiyetec.entity.MemberProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * M03 — 会员档案子行业画像扩展回归测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code MemberProfile.hairType}（美发子行业）</li>
 *   <li>{@code MemberProfile.nailType}（美甲子行业）</li>
 * </ul>
 *
 * <p>不加载 Spring 上下文，pure-reflection 即可。
 */
class MemberProfileHairNailTest {

    @Test
    void member_profile_has_hairType() {
        Field f = findField(MemberProfile.class, "hairType");
        assertNotNull(f, "MemberProfile.hairType 字段必须存在（美发子行业画像）");
        assertEquals(String.class, f.getType());
    }

    @Test
    void member_profile_has_nailType() {
        Field f = findField(MemberProfile.class, "nailType");
        assertNotNull(f, "MemberProfile.nailType 字段必须存在（美甲子行业画像）");
        assertEquals(String.class, f.getType());
    }

    @Test
    void member_profile_hair_nail_are_writable() {
        MemberProfile p = new MemberProfile();
        p.setHairType("受损发质");
        p.setNailType("薄软甲");
        assertEquals("受损发质", p.getHairType());
        assertEquals("薄软甲", p.getNailType());
    }

    private static Field findField(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
