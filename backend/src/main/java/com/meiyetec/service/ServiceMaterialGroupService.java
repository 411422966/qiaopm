package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.ServiceMaterialGroup;
import java.util.List;
public interface ServiceMaterialGroupService extends IService<ServiceMaterialGroup> {    List<ServiceMaterialGroup> list(Long serviceId);    boolean save(ServiceMaterialGroup group);    boolean update(ServiceMaterialGroup group);    boolean delete(Long id);}
