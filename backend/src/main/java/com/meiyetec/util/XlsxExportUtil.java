package com.meiyetec.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R2.0-D T-D05：轻量 xlsx 导出工具（基于 Apache POI SXSSF 流式写入）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>用 {@link SXSSFWorkbook} 内存不爆炸，单 sheet</li>
 *   <li>第一行写租户水印 + 生成时间</li>
 *   <li>第二行写表头</li>
 *   <li>后续行写数据</li>
 * </ul>
 */
public class XlsxExportUtil {

    /**
     * @param sheetName sheet 名
     * @param watermark 顶部水印行（如 "库存清单 · 租户：xxx · 生成时间：2026-08-12 ..."）
     * @param headers   表头数组
     * @param rows      每一行数据，按 headers 顺序
     * @return xlsx 字节流
     */
    public static byte[] export(String sheetName,
                                String watermark,
                                String[] headers,
                                List<List<Object>> rows) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sh = wb.createSheet(sheetName);
            // 行 1：租户水印（合并 A1:H1 加粗）
            Row titleRow = sh.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(watermark);
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            if (headers.length > 1) {
                sh.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
            }
            // 行 2：表头（加粗 + 背景色）
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row headerRow = sh.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }
            // 数据行
            int rowIdx = 2;
            for (List<Object> row : rows) {
                Row r = sh.createRow(rowIdx++);
                for (int i = 0; i < row.size() && i < headers.length; i++) {
                    Object v = row.get(i);
                    Cell c = r.createCell(i);
                    if (v == null) {
                        c.setBlank();
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(String.valueOf(v));
                    }
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    /** null 安全转字符串 */
    public static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /** null 安全转 Integer */
    public static Integer integer(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }

    /** null 安全转 Double */
    public static Double dbl(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    /** null 安全转 BigDecimal */
    public static java.math.BigDecimal bigDec(Object o) {
        if (o == null) return null;
        if (o instanceof java.math.BigDecimal b) return b;
        if (o instanceof Number n) return new java.math.BigDecimal(n.toString());
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        return new java.math.BigDecimal(s);
    }

    /**
     * 读取 xlsx 字节流，跳过顶部水印行（如有），把数据行解析成 Map 列表。
     * <p>第一行约定为水印（可空），第二行表头，后续行数据；空行自动跳过。</p>
     * <p>表头 → Map key 的映射由调用方通过 expectedHeaders 提供（顺序敏感、大小写不敏感）。</p>
     *
     * @param in              xlsx 输入流
     * @param expectedHeaders 表头映射（key=Map里的key, value=期望表头名），传 null 则用原始字符串作为 key
     * @return 行数据列表（每行 Map<key, String|null>）
     */
    public static List<Map<String, Object>> importRows(InputStream in, Map<String, String> expectedHeaders) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return out;
            // 找表头行：约定第 1 行水印 + 第 2 行表头；如有 expectedHeaders，扫描前 3 行匹配
            int headerRowIdx = 1;
            if (expectedHeaders != null && !expectedHeaders.isEmpty()) {
                boolean matched = false;
                for (int i = 0; i < Math.min(3, sheet.getLastRowNum() + 1); i++) {
                    Row r = sheet.getRow(i);
                    if (r == null) continue;
                    String first = str(r.getCell(0));
                    if (first == null) continue;
                    if (expectedHeaders.containsValue(first.trim())
                            || expectedHeaders.containsKey(first.trim())) {
                        headerRowIdx = i;
                        matched = true;
                        break;
                    }
                }
                if (!matched) headerRowIdx = 1;
            }
            Row headerRow = sheet.getRow(headerRowIdx);
            if (headerRow == null) return out;
            int colCount = headerRow.getLastCellNum();
            // 解析表头
            List<String> keys = new ArrayList<>(colCount);
            for (int c = 0; c < colCount; c++) {
                String h = str(headerRow.getCell(c));
                if (expectedHeaders != null && !expectedHeaders.isEmpty()) {
                    // 反向匹配：找到 expectedHeaders 中 value == h 的 key
                    String matchedKey = null;
                    for (Map.Entry<String, String> e : expectedHeaders.entrySet()) {
                        if (e.getValue().equalsIgnoreCase(h)) { matchedKey = e.getKey(); break; }
                    }
                    keys.add(matchedKey != null ? matchedKey : h);
                } else {
                    keys.add(h);
                }
            }
            // 解析数据行
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                // 跳过全空行
                boolean allBlank = true;
                Map<String, Object> map = new HashMap<>();
                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.getCell(c);
                    String val = cellToString(cell);
                    if (val != null && !val.isEmpty()) allBlank = false;
                    if (c < keys.size()) map.put(keys.get(c), val);
                }
                if (!allBlank) out.add(map);
            }
        }
        return out;
    }

    /** Cell → String（统一类型） */
    private static String cellToString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(cell)) yield cell.getLocalDateTimeCellValue().toString();
                long lv = (long) d;
                yield (double) lv == d ? String.valueOf(lv) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { yield str(cell.getStringCellValue()); }
            }
            default -> null;
        };
    }
}