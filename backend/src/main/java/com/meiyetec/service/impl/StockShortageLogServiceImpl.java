package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.StockShortageLog;
import com.meiyetec.mapper.StockShortageLogMapper;
import com.meiyetec.service.StockShortageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class StockShortageLogServiceImpl extends ServiceImpl<StockShortageLogMapper, StockShortageLog>        implements StockShortageLogService {    
private final StockShortageLogMapper stockShortageLogMapper;    
@Override    
public List<StockShortageLog> queryList(String status, Long storeId) {        return stockShortageLogMapper.queryList(status, storeId);    }}
