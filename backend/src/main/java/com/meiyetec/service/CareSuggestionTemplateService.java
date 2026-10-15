package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CareSuggestionTemplate;
import java.util.List;
public interface CareSuggestionTemplateService extends IService<CareSuggestionTemplate> {    List<CareSuggestionTemplate> list(String templateType, Integer status);    CareSuggestionTemplate getByCode(String code);}
