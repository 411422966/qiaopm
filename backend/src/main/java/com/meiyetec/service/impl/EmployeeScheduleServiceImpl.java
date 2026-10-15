package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.EmployeeSchedule;
import com.meiyetec.mapper.EmployeeScheduleMapper;
import com.meiyetec.service.EmployeeScheduleService;
import org.springframework.stereotype.Service;

@Service
public class EmployeeScheduleServiceImpl extends ServiceImpl<EmployeeScheduleMapper, EmployeeSchedule> implements EmployeeScheduleService {
}