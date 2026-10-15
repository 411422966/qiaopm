package com.meiyetec.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 JDBC / SQL 查询返回的 snake_case Map / List<Map> 转成 camelCase，
 * 统一给前端。
 */
public final class Keys {

    private static final Pattern UNDERSCORE = Pattern.compile("_([a-z0-9])");
    private static final Random RANDOM = new Random();

    private Keys() {}

    public static String camel(String name) {
        if (name == null || name.isEmpty()) return name;
        Matcher m = UNDERSCORE.matcher(name);
        StringBuilder sb = new StringBuilder(name.length());
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1).toUpperCase()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Map<String, Object> camel(Map<String, Object> row) {
        if (row == null) return null;
        Map<String, Object> out = new HashMap<>(row.size());
        for (Map.Entry<String, Object> e : row.entrySet()) {
            out.put(camel(e.getKey()), e.getValue());
        }
        return out;
    }

    public static List<Map<String, Object>> camel(List<Map<String, Object>> rows) {
        if (rows == null) return null;
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(camel(r));
        return out;
    }

    public static String randomCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}