package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ResourceConflictLog;
import java.util.List;
public interface ResourceConflictLogService extends IService<ResourceConflictLog> {    List<ResourceConflictLog> queryList(Long storeId, String conflictType);}
