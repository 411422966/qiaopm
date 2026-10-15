package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MemberStylePreference;
import com.meiyetec.service.MemberStylePreferenceService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/style/preference")
@RequiredArgsConstructor
@SysOpLog(module = "Member Style Preference")
public class MemberStylePreferenceController {

    private final MemberStylePreferenceService preferenceService;

    @GetMapping("/{memberId}")
    public Result<MemberStylePreference> getByMember(@PathVariable Long memberId) {
        return Result.success(preferenceService.getOrCreate(memberId));
    }

    @PutMapping("/{memberId}")
    public Result<Void> update(@PathVariable Long memberId, @RequestBody MemberStylePreference pref) {
        pref.setMemberId(memberId);
        if (pref.getId() != null) {
            preferenceService.updateById(pref);
        } else {
            preferenceService.save(pref);
        }
        return Result.success("更新成功", null);
    }

    @PostMapping("/{memberId}/browse/{styleId}")
    public Result<Void> recordBrowse(@PathVariable Long memberId, @PathVariable Long styleId) {
        preferenceService.recordBrowse(memberId, styleId);
        return Result.success(null, null);
    }

    @PostMapping("/{memberId}/favorite/{styleId}")
    public Result<Boolean> toggleFavorite(@PathVariable Long memberId, @PathVariable Long styleId) {
        return Result.success(preferenceService.toggleFavorite(memberId, styleId));
    }
}
