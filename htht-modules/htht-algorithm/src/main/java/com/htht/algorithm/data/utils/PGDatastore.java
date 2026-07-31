package com.htht.algorithm.data.utils;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class PGDatastore {
    private static DataStore dataStore = null;

    private String host;
    private String port;
    private String dbname;
    private String schema;
    private String username;
    private String password;

    public static DataStore getDefaultDatastore() {
        if (dataStore == null) {
            Map<String, Object> params = new HashMap<>();
            params.put(PostgisNGDataStoreFactory.DBTYPE.key, "postgis");
            params.put(PostgisNGDataStoreFactory.HOST.key, "192.168.2.118");
            params.put(PostgisNGDataStoreFactory.PORT.key, new Integer(65432));
            params.put(PostgisNGDataStoreFactory.DATABASE.key, "shallow_topography");
            params.put(PostgisNGDataStoreFactory.SCHEMA.key, "public");
            params.put(PostgisNGDataStoreFactory.USER.key, "postgres");
            params.put(PostgisNGDataStoreFactory.PASSWD.key, "hqxkj168++");

            try {
                dataStore = DataStoreFinder.getDataStore(params);
            } catch (IOException e) {
                log.error("默认Postgis数据库连接失败", e);
            }
        }
        return dataStore;
    }

    public DataStore getDataStore() {
        if (dataStore == null) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put(PostgisNGDataStoreFactory.DBTYPE.key, "postgis");
            params.put(PostgisNGDataStoreFactory.HOST.key, host);
            params.put(PostgisNGDataStoreFactory.PORT.key, new Integer(port));
            params.put(PostgisNGDataStoreFactory.DATABASE.key, dbname);
            params.put(PostgisNGDataStoreFactory.SCHEMA.key, schema);
            params.put(PostgisNGDataStoreFactory.USER.key, username);
            params.put(PostgisNGDataStoreFactory.PASSWD.key, password);
            try {
                dataStore = DataStoreFinder.getDataStore(params);
            } catch (IOException e) {
                log.error("Postgis数据库连接失败", e);
            }
        }
        if (dataStore == null) {
            dataStore = getDefaultDatastore();
        }
        return dataStore;
    }

    public void dispose() {
        dataStore.dispose();
        dataStore = null;
    }
}
