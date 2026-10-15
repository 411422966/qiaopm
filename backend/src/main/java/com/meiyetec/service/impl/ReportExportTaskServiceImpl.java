package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ReportExportTask;
import com.meiyetec.mapper.ReportExportTaskMapper;
import com.meiyetec.service.ReportExportTaskService;
import org.springframework.stereotype.Service;

@Service
public class ReportExportTaskServiceImpl extends ServiceImpl<ReportExportTaskMapper, ReportExportTask>
        implements ReportExportTaskService {
}