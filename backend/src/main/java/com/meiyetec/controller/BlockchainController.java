package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.BlockchainRecord;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.BlockchainBusinessService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/blockchain")
@RequiredArgsConstructor
public class BlockchainController {

    private final BlockchainBusinessService blockchainService;

    @PostMapping("/record")
    @RequirePermission("blockchain:record")
    public Result<BlockchainRecord> record(@RequestBody Map<String, Object> body) {
        String recordType = (String) body.get("recordType");
        Long bizId = body.get("bizId") == null ? null : ((Number) body.get("bizId")).longValue();
        String bizNo = (String) body.get("bizNo");
        String payload = body.get("payload") == null ? null : body.get("payload").toString();
        String chainId = (String) body.get("chainId");
        Integer difficulty = body.get("difficulty") == null ? null : ((Number) body.get("difficulty")).intValue();
        String operatorName = (String) body.getOrDefault("operatorName", currentUser());
        return Result.success("已存证",
                blockchainService.record(recordType, bizId, bizNo, payload, chainId, difficulty, operatorName));
    }

    @PostMapping("/record/product")
    @RequirePermission("blockchain:record")
    public Result<BlockchainRecord> recordProduct(@RequestBody Map<String, Object> body) {
        return Result.success("商品已上链", blockchainService.recordProduct(
                numLong(body.get("productId")),
                str(body.get("productNo")),
                str(body.get("productName")),
                str(body.get("batchNo")),
                str(body.get("manufacturer")),
                numDec(body.get("price")),
                str(body.getOrDefault("operatorName", currentUser()))));
    }

    @PostMapping("/record/drug")
    @RequirePermission("blockchain:record")
    public Result<BlockchainRecord> recordDrug(@RequestBody Map<String, Object> body) {
        return Result.success("药品已上链", blockchainService.recordDrug(
                numLong(body.get("drugId")),
                str(body.get("drugNo")),
                str(body.get("drugName")),
                str(body.get("batchNo")),
                str(body.get("approvalNo")),
                str(body.get("expiryDate")),
                str(body.getOrDefault("operatorName", currentUser()))));
    }

    @PostMapping("/record/device")
    @RequirePermission("blockchain:record")
    public Result<BlockchainRecord> recordDevice(@RequestBody Map<String, Object> body) {
        return Result.success("器械已上链", blockchainService.recordDevice(
                numLong(body.get("deviceId")),
                str(body.get("deviceNo")),
                str(body.get("deviceName")),
                str(body.get("registrationNo")),
                str(body.get("serialNo")),
                str(body.getOrDefault("operatorName", currentUser()))));
    }

    @PostMapping("/record/order")
    @RequirePermission("blockchain:record")
    public Result<BlockchainRecord> recordOrder(@RequestBody Map<String, Object> body) {
        return Result.success("订单已上链", blockchainService.recordOrder(
                numLong(body.get("orderId")),
                str(body.get("orderNo")),
                numDec(body.get("amount")),
                str(body.get("paymentMethod")),
                str(body.getOrDefault("operatorName", currentUser()))));
    }

    @PostMapping("/record/consent")
    @RequirePermission("blockchain:record")
    public Result<BlockchainRecord> recordConsent(@RequestBody Map<String, Object> body) {
        return Result.success("同意书已上链", blockchainService.recordConsent(
                numLong(body.get("consentId")),
                str(body.get("consentNo")),
                str(body.get("consentType")),
                numLong(body.get("memberId")),
                str(body.get("memberName")),
                str(body.getOrDefault("operatorName", currentUser()))));
    }

    @GetMapping("/record/{bizId}")
    @RequirePermission("blockchain:record")
    public Result<BlockchainRecord> getRecord(@RequestParam String recordType,
                                               @PathVariable Long bizId) {
        return Result.success(blockchainService.getRecord(recordType, bizId));
    }

    @GetMapping("/record/page")
    @RequirePermission("blockchain:record")
    public Result<Map<String, Object>> recordPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String recordType) {
        return Result.success(blockchainService.pageRecords(pageNum, pageSize, recordType));
    }

    @GetMapping("/chain/verify")
    @RequirePermission("blockchain:chain")
    public Result<Map<String, Object>> verifyChain(@RequestParam(required = false) String chainId) {
        return Result.success(blockchainService.verifyChain(chainId));
    }

    @GetMapping("/chain/info")
    @RequirePermission("blockchain:chain")
    public Result<Map<String, Object>> chainInfo(@RequestParam(required = false) String chainId) {
        return Result.success(blockchainService.chainInfo(chainId));
    }

    @GetMapping("/chain/overview")
    @RequirePermission("blockchain:chain")
    public Result<List<Map<String, Object>>> chainOverview() {
        String[] chains = {"meiyetec-main", "meiyetec-cosmetic", "meiyetec-drug", "meiyetec-device"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String c : chains) {
            rows.add(blockchainService.chainInfo(c));
        }
        return Result.success(rows);
    }

    @GetMapping("/trace/{recordType}/{bizId}")
    @RequirePermission("blockchain:trace")
    public Result<Map<String, Object>> trace(@PathVariable String recordType,
                                             @PathVariable Long bizId) {
        return Result.success(blockchainService.trace(recordType, bizId));
    }

    @PostMapping("/utils/hash")
    @RequirePermission("blockchain:record")
    public Result<Map<String, Object>> hash(@RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        String sha = blockchainService.sha256Hex(text == null ? "" : text);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("input", text);
        data.put("sha256", sha);
        data.put("length", sha.length());
        return Result.success(data);
    }

    @GetMapping("/stats/chain")
    @RequirePermission("blockchain:monitor")
    public Result<List<Map<String, Object>>> chainStats() {
        return Result.success(blockchainService.chainStats());
    }

    @GetMapping("/stats/record-type")
    @RequirePermission("blockchain:monitor")
    public Result<List<Map<String, Object>>> recordTypeStats() {
        return Result.success(blockchainService.recordTypeStats());
    }

    @GetMapping("/stats/trend")
    @RequirePermission("blockchain:monitor")
    public Result<List<Map<String, Object>>> dailyTrend(
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(blockchainService.dailyTrend(days));
    }

    @GetMapping("/stats/overview")
    @RequirePermission("blockchain:monitor")
    public Result<Map<String, Object>> overview() {
        return Result.success(blockchainService.overview());
    }

    private static Long numLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static BigDecimal numDec(Object o) {
        return o == null ? null : new BigDecimal(o.toString());
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private String currentUser() {
        try {
            return TenantContextHolder.get().getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}