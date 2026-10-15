package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.DetectionDiagnosisService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crm/detection/diagnose")
@RequiredArgsConstructor
@SysOpLog(module = "DetectionDiagnosis")
public class DetectionDiagnosisController {

    private final DetectionDiagnosisService detectionDiagnosisService;

    @GetMapping("/all/{memberId}")
    public Result<List<String>> diagnoseAll(@PathVariable Long memberId) {
        return Result.success(detectionDiagnosisService.diagnoseAll(memberId));
    }
}
