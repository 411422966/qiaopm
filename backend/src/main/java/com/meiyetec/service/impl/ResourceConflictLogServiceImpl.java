package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ResourceConflictLog;
import com.meiyetec.mapper.ResourceConflictLogMapper;
import com.meiyetec.service.ResourceConflictLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class ResourceConflictLogServiceImpl extends ServiceImpl<ResourceConflictLogMapper, ResourceConflictLog>        implements ResourceConflictLogService {    
private final ResourceConflictLogMapper resourceConflictLogMapper;    
@Override    
public List<ResourceConflictLog> queryList(Long storeId, String conflictType) {        return resourceConflictLogMapper.queryList(storeId, conflictType);    }}
