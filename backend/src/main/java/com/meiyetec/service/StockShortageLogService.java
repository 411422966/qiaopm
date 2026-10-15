package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.StockShortageLog;
import java.util.List;
public interface StockShortageLogService extends IService<StockShortageLog> {    List<StockShortageLog> queryList(String status, Long storeId);}
