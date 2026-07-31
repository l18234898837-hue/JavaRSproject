ALTER TABLE public.ws_t_island_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_info.create_by IS '创建人';
ALTER TABLE public.ws_t_island_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_info.update_by IS '更新人';
ALTER TABLE public.ws_t_island_info ALTER COLUMN id TYPE bigint USING id::bigint;
update ws_t_island_info set deleted = 0 where 1=1;


ALTER TABLE public.ws_t_reef_work ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_reef_work ALTER COLUMN island_id TYPE bigint USING island_id::bigint;
ALTER TABLE public.ws_t_reef_work ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_reef_work.create_by IS '创建人';
ALTER TABLE public.ws_t_reef_work ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_reef_work.update_by IS '更新人';
ALTER TABLE public.ws_t_reef_work ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_reef_work.deleted IS '删除标识，1是0否';
update ws_t_reef_work set deleted = 0 where 1=1;


ALTER TABLE public.ws_t_literature_subject_statistics ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_subject_statistics ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_subject_statistics.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_subject_statistics ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_subject_statistics.update_by IS '更新人';
ALTER TABLE public.ws_t_reef_work ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_reef_work.deleted IS '删除标识，1是0否';
update ws_t_reef_work set deleted = 0 where 1=1;


ALTER TABLE public.ws_t_research_achievement ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_research_achievement ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_research_achievement.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_research_achievement ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_research_achievement.create_by IS '创建人';
ALTER TABLE public.ws_t_research_achievement ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_research_achievement.update_by IS '更新人';
update ws_t_research_achievement set deleted = 0 where 1=1;

ALTER TABLE public.ws_t_research_achievement ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_research_achievement ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_research_achievement.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_research_achievement ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_research_achievement.create_by IS '创建人';
ALTER TABLE public.ws_t_research_achievement ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_research_achievement.update_by IS '更新人';
ALTER TABLE public.ws_t_research_achievement ALTER COLUMN investigate_event_id TYPE bigint USING investigate_event_id::bigint;
update ws_t_research_achievement set deleted = 0 where 1=1;


ALTER TABLE public.ws_t_station_info ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_station_info ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_station_info.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_station_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_station_info.create_by IS '创建人';
ALTER TABLE public.ws_t_station_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_station_info.update_by IS '更新人';
update ws_t_station_info set deleted = 0 where 1=1;


ALTER TABLE public.ws_t_investigate_event ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_investigate_event ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_investigate_event.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_investigate_event ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_investigate_event.create_by IS '创建人';
ALTER TABLE public.ws_t_investigate_event ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_investigate_event.update_by IS '更新人';
update ws_t_investigate_event set deleted = 0
ALTER TABLE public.ws_t_investigate_event ALTER COLUMN voyage_id TYPE bigint USING voyage_id::bigint;


ALTER TABLE public.ws_t_actual_measurement_route ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_actual_measurement_route ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_route.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_actual_measurement_route ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_route.create_by IS '创建人';
ALTER TABLE public.ws_t_actual_measurement_route ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_route.update_by IS '更新人';
update ws_t_actual_measurement_route set deleted = 0 ;
ALTER TABLE public.ws_t_actual_measurement_route ALTER COLUMN investigate_event_id TYPE bigint USING investigate_event_id::bigint;
ALTER TABLE public.ws_t_actual_measurement_route ADD CONSTRAINT ws_t_actual_measurement_route_unique UNIQUE (id);


ALTER TABLE public.ws_t_actual_measurement_site ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_actual_measurement_site ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_site.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_actual_measurement_site ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_site.create_by IS '创建人';
ALTER TABLE public.ws_t_actual_measurement_site ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_site.update_by IS '更新人';
update ws_t_actual_measurement_site set deleted = 0 ;
ALTER TABLE public.ws_t_actual_measurement_site ALTER COLUMN route_id TYPE bigint USING route_id::bigint;


ALTER TABLE public.ws_t_aws_data ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_aws_data ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_aws_data.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_aws_data ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_aws_data.create_by IS '创建人';
ALTER TABLE public.ws_t_aws_data ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_aws_data.update_by IS '更新人';
update ws_t_aws_data set deleted = 0 ;
ALTER TABLE public.ws_t_aws_data ALTER COLUMN route_id TYPE bigint USING route_id::bigint;


ALTER TABLE public.ws_t_adcp_data ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_adcp_data ALTER COLUMN route_id TYPE bigint USING route_id::bigint;
ALTER TABLE public.ws_t_adcp_data ADD update_time timestamp(6) NULL;
COMMENT ON COLUMN public.ws_t_adcp_data.update_time IS '更新时间';
ALTER TABLE public.ws_t_adcp_data ADD create_time timestamp(6) NULL;
COMMENT ON COLUMN public.ws_t_adcp_data.create_time IS '创建时间';
ALTER TABLE public.ws_t_adcp_data ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_adcp_data.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_adcp_data ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_adcp_data.create_by IS '创建人';
ALTER TABLE public.ws_t_adcp_data ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_adcp_data.update_by IS '更新人';
update ws_t_adcp_data set deleted = 0 ;


ALTER TABLE public.ws_t_actual_measurement_site_data ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_actual_measurement_site_data ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_site_data.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_actual_measurement_site_data ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_site_data.create_by IS '创建人';
ALTER TABLE public.ws_t_actual_measurement_site_data ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_actual_measurement_site_data.update_by IS '更新人';
update ws_t_actual_measurement_site_data set deleted = 0 ;
ALTER TABLE public.ws_t_actual_measurement_site_data ALTER COLUMN actual_measurement_site_id TYPE bigint USING actual_measurement_site_id::bigint;


ALTER TABLE public.ws_data_type_info ADD deleted int NULL;
COMMENT ON COLUMN public.ws_data_type_info.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_data_type_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_data_type_info.create_by IS '创建人';
ALTER TABLE public.ws_data_type_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_data_type_info.update_by IS '更新人';
update ws_data_type_info set deleted = 0 ;

ALTER TABLE public.ws_data_process ADD deleted int NULL;
COMMENT ON COLUMN public.ws_data_process.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_data_process ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_data_process.create_by IS '创建人';
ALTER TABLE public.ws_data_process ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_data_process.update_by IS '更新人';
update ws_data_process set deleted = 0 ;

ALTER TABLE public.ws_t_island_area ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_island_area ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_area.create_by IS '创建人';
ALTER TABLE public.ws_t_island_area ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_area.update_by IS '更新人';

ALTER TABLE public.ws_t_island_area_compare ALTER COLUMN id TYPE bigint USING id::bigint;
COMMENT ON COLUMN public.ws_t_island_area_compare.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_island_area_compare ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_area_compare.create_by IS '创建人';
ALTER TABLE public.ws_t_island_area_compare ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_area_compare.update_by IS '更新人';
ALTER TABLE public.ws_t_island_area_compare ALTER COLUMN island_id TYPE bigint USING island_id::bigint;


ALTER TABLE public.ws_t_rs_original_data ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_rs_original_data ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_rs_original_data ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data.create_by IS '创建人';
ALTER TABLE public.ws_t_rs_original_data ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data.update_by IS '更新人';
update ws_t_rs_original_data set deleted = 0 ;


ALTER TABLE public.ws_t_rs_original_data_ref ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_rs_original_data_ref ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_ref.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_rs_original_data_ref ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_ref.create_by IS '创建人';
ALTER TABLE public.ws_t_rs_original_data_ref ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_ref.update_by IS '更新人';
update ws_t_rs_original_data_ref set deleted = 0 ;
ALTER TABLE public.ws_t_rs_original_data_ref ALTER COLUMN original_data_id TYPE bigint USING original_data_id::bigint;


ALTER TABLE public.ws_t_rs_original_data_detail ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_rs_original_data_detail ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_detail.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_rs_original_data_detail ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_detail.create_by IS '创建人';
ALTER TABLE public.ws_t_rs_original_data_detail ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_detail.update_by IS '更新人';
update ws_t_rs_original_data_detail set deleted = 0 ;
ALTER TABLE public.ws_t_rs_original_data_detail ALTER COLUMN rs_original_data_id TYPE bigint USING rs_original_data_id::bigint;


ALTER TABLE public.ws_t_original_data_save_record ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_original_data_save_record ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_original_data_save_record.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_original_data_save_record ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_original_data_save_record.create_by IS '创建人';
ALTER TABLE public.ws_t_original_data_save_record ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_original_data_save_record.update_by IS '更新人';
update ws_t_original_data_save_record set deleted = 0 ;
ALTER TABLE public.ws_t_original_data_save_record ALTER COLUMN original_data_id TYPE bigint USING original_data_id::bigint;


ALTER TABLE public.ws_t_rs_original_data_result ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_result.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_rs_original_data_result ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_result.create_by IS '创建人';
ALTER TABLE public.ws_t_rs_original_data_result ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_rs_original_data_result.update_by IS '更新人';
update ws_t_rs_original_data_result set deleted = 0 ;
ALTER TABLE public.ws_t_rs_original_data_result ALTER COLUMN original_data_id TYPE bigint USING original_data_id::bigint;


ALTER TABLE public.ws_t_ais_point_info ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_ais_point_info ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_ais_point_info.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_ais_point_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_ais_point_info.create_by IS '创建人';
ALTER TABLE public.ws_t_ais_point_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_ais_point_info.update_by IS '更新人';
update ws_t_ais_point_info set deleted = 0 ;
COMMENT ON TABLE public.ws_t_ais_point_info IS 'ais船舶航线';


ALTER TABLE public.ws_t_ship_info ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_ship_info ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_ship_info.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_ship_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_ship_info.create_by IS '创建人';
ALTER TABLE public.ws_t_ship_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_ship_info.update_by IS '更新人';
update ws_t_ship_info set deleted = 0 ;


ALTER TABLE public.ws_t_ais_point_event_info ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_ais_point_event_info ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_ais_point_event_info.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_ais_point_event_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_ais_point_event_info.create_by IS '创建人';
ALTER TABLE public.ws_t_ais_point_event_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_ais_point_event_info.update_by IS '更新人';
update ws_t_ais_point_event_info set deleted = 0 ;
ALTER TABLE public.ws_t_ais_point_event_info ADD create_time timestamp(6) NULL;
COMMENT ON COLUMN public.ws_t_ais_point_event_info.create_time IS '创建时间';


ALTER TABLE public.ws_t_carousel_chart ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_carousel_chart ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_carousel_chart.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_carousel_chart ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_carousel_chart.create_by IS '创建人';
ALTER TABLE public.ws_t_carousel_chart ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_carousel_chart.update_by IS '更新人';
update ws_t_carousel_chart set deleted = 0 ;
ALTER TABLE public.ws_t_carousel_chart ADD update_time timestamp(6) NULL;
COMMENT ON COLUMN public.ws_t_carousel_chart.update_time IS '更新时间';
ALTER TABLE public.ws_t_carousel_chart ADD create_time timestamp(6) NULL;
COMMENT ON COLUMN public.ws_t_carousel_chart.create_time IS '创建时间';


ALTER TABLE public.ws_t_literature_info ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_info ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_literature_info.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_literature_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_info.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_info.update_by IS '更新人';
update ws_t_literature_info set deleted = 0 ;


ALTER TABLE public.ws_t_literature_author_statistics ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_author_statistics ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_literature_author_statistics.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_literature_author_statistics ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_author_statistics.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_author_statistics ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_author_statistics.update_by IS '更新人';
update ws_t_literature_author_statistics set deleted = 0 ;


ALTER TABLE public.ws_t_literature_research_hotspot_statistics ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_research_hotspot_statistics ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_literature_research_hotspot_statistics.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_literature_research_hotspot_statistics ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_research_hotspot_statistics.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_research_hotspot_statistics ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_research_hotspot_statistics.update_by IS '更新人';
update ws_t_literature_research_hotspot_statistics set deleted = 0 ;
ALTER TABLE public.ws_t_literature_research_hotspot_statistics ALTER COLUMN update_time TYPE timestamp(6) USING update_time::timestamp(6);


ALTER TABLE public.ws_t_literature_year_statistics ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_year_statistics ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_literature_year_statistics.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_literature_year_statistics ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_year_statistics.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_year_statistics ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_year_statistics.update_by IS '更新人';
update ws_t_literature_year_statistics set deleted = 0 ;


ALTER TABLE public.ws_t_literature_country_statistics ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_country_statistics ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_literature_country_statistics.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_literature_country_statistics ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_country_statistics.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_country_statistics ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_country_statistics.update_by IS '更新人';
update ws_t_literature_country_statistics set deleted = 0 ;


ALTER TABLE public.ws_t_literature_institution_statistics ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_institution_statistics ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_literature_institution_statistics.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_literature_institution_statistics ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_institution_statistics.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_institution_statistics ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_institution_statistics.update_by IS '更新人';
update ws_t_literature_institution_statistics set deleted = 0 ;


ALTER TABLE public.ws_t_literature_collaborate_institution_info ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_literature_collaborate_institution_info ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_literature_collaborate_institution_info.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_literature_collaborate_institution_info ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_collaborate_institution_info.create_by IS '创建人';
ALTER TABLE public.ws_t_literature_collaborate_institution_info ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_literature_collaborate_institution_info.update_by IS '更新人';
update ws_t_literature_collaborate_institution_info set deleted = 0 ;


ALTER TABLE public.ws_t_island_rs_image ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_island_rs_image ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_island_rs_image.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_island_rs_image ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_rs_image.create_by IS '创建人';
ALTER TABLE public.ws_t_island_rs_image ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_island_rs_image.update_by IS '更新人';
update ws_t_island_rs_image set deleted = 0 ;
ALTER TABLE public.ws_t_island_rs_image ALTER COLUMN island_id TYPE bigint USING island_id::bigint;


ALTER TABLE public.ws_t_satellite_element ALTER COLUMN id TYPE bigint USING id::bigint;
ALTER TABLE public.ws_t_satellite_element ADD deleted int NULL;
COMMENT ON COLUMN public.ws_t_satellite_element.deleted IS '删除标识，1是0否';
ALTER TABLE public.ws_t_satellite_element ADD create_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_satellite_element.create_by IS '创建人';
ALTER TABLE public.ws_t_satellite_element ADD update_by varchar(50) NULL;
COMMENT ON COLUMN public.ws_t_satellite_element.update_by IS '更新人';
update ws_t_satellite_element set deleted = 0 ;
ALTER TABLE public.ws_t_satellite_element ADD update_time timestamp(6) NULL;
COMMENT ON COLUMN public.ws_t_satellite_element.update_time IS '更新时间';
ALTER TABLE public.ws_t_satellite_element ADD create_time timestamp(6) NULL;
COMMENT ON COLUMN public.ws_t_satellite_element.create_time IS '创建时间';
































