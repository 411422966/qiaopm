package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MemberLevel;
import com.meiyetec.mapper.MemberLevelMapper;
import com.meiyetec.service.MemberLevelService;
import org.springframework.stereotype.Service;

@Service
public class MemberLevelServiceImpl extends ServiceImpl<MemberLevelMapper, MemberLevel> implements MemberLevelService {
}
