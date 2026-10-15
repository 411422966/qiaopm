package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MemberStylePreference;
import com.meiyetec.entity.StyleBrowseLog;
import com.meiyetec.mapper.MemberStylePreferenceMapper;
import com.meiyetec.mapper.StyleBrowseLogMapper;
import com.meiyetec.service.MemberStylePreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberStylePreferenceServiceImpl extends ServiceImpl<MemberStylePreferenceMapper, MemberStylePreference>
        implements MemberStylePreferenceService {

    private final MemberStylePreferenceMapper preferenceMapper;
    private final StyleBrowseLogMapper browseLogMapper;

    @Override
    public MemberStylePreference getOrCreate(Long memberId) {
        MemberStylePreference pref = preferenceMapper.findByMember(memberId);
        if (pref == null) {
            pref = new MemberStylePreference();
            pref.setMemberId(memberId);
            pref.setRecommendCount(0);
            save(pref);
        }
        return pref;
    }

    @Override
    public void recordBrowse(Long memberId, Long styleId) {
        StyleBrowseLog log = new StyleBrowseLog();
        log.setMemberId(memberId);
        log.setStyleId(styleId);
        log.setActionType("view");
        log.setActionTime(LocalDateTime.now());
        browseLogMapper.insert(log);
        MemberStylePreference pref = getOrCreate(memberId);
        Set<String> recent = new LinkedHashSet<>();
        if (pref.getRecentStyles() != null && !pref.getRecentStyles().isEmpty()) {
            recent.addAll(Arrays.asList(pref.getRecentStyles().split(",")));
        }
        recent.add(String.valueOf(styleId));
        if (recent.size() > 10) {
            Set<String> trimmed = new LinkedHashSet<>();
            int start = recent.size() - 10;
            int idx = 0;
            for (String s : recent) {
                if (idx++ >= start) trimmed.add(s);
            }
            recent = trimmed;
        }
        pref.setRecentStyles(String.join(",", recent));
        updateById(pref);
    }

    @Override
    public boolean toggleFavorite(Long memberId, Long styleId) {
        MemberStylePreference pref = getOrCreate(memberId);
        Set<String> favorites = new LinkedHashSet<>();
        if (pref.getFavoriteStyles() != null && !pref.getFavoriteStyles().isEmpty()) {
            favorites.addAll(Arrays.asList(pref.getFavoriteStyles().split(",")));
        }
        String sid = String.valueOf(styleId);
        boolean isFavorited;
        if (favorites.contains(sid)) {
            favorites.remove(sid);
            isFavorited = false;
        } else {
            favorites.add(sid);
            isFavorited = true;
            StyleBrowseLog log = new StyleBrowseLog();
            log.setMemberId(memberId);
            log.setStyleId(styleId);
            log.setActionType("favorite");
            log.setActionTime(LocalDateTime.now());
            browseLogMapper.insert(log);
        }
        pref.setFavoriteStyles(String.join(",", favorites));
        updateById(pref);
        return isFavorited;
    }
}
