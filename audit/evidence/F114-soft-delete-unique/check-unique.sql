SELECT table_name, IFNULL(GROUP_CONCAT(CONCAT(index_name,'=(',GROUP_CONCAT(column_name ORDER BY seq_in_index),')')),'(none)') AS unique_idx
FROM information_schema.statistics
WHERE table_schema='meiyetec'
  AND table_name IN ('mkt_channel_template','mkt_coupon','mkt_coupon_batch','mkt_dynamic_pricing_rule','mkt_journey','mkt_promotion','mkt_segment','marketing_touch_task','mkt_channel_config','mkt_invitation','mkt_invitation_rule','mkt_dispatch','mkt_dispatch_target','mkt_effect_snapshot','mkt_journey_instance','market_activity')
  AND Non_unique=0
GROUP BY table_name
ORDER BY table_name;