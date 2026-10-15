package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.Member;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.service.MemberService;
import org.springframework.stereotype.Service;

@Service
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements MemberService {
}
