package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ServiceMaterialGroup;
import com.meiyetec.mapper.ServiceMaterialGroupMapper;
import com.meiyetec.service.ServiceMaterialGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class ServiceMaterialGroupServiceImpl extends ServiceImpl<ServiceMaterialGroupMapper, ServiceMaterialGroup>        implements ServiceMaterialGroupService {    
private final ServiceMaterialGroupMapper serviceMaterialGroupMapper;    
@Override    
public List<ServiceMaterialGroup> list(Long serviceId) {        return serviceMaterialGroupMapper.findWithDetails(serviceId);    }    
@Override    
public boolean save(ServiceMaterialGroup group) {        return save(group);    }    
@Override    
public boolean update(ServiceMaterialGroup group) {        return updateById(group);    }    
@Override    
public boolean delete(Long id) {        return removeById(id);    }}
