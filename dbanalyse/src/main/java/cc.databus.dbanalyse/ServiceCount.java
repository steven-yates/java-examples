package cc.databus.dbanalyse;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Count the instance count for each companies' collector
 */
public class ServiceCount {

    public static void main(String[] args) throws ClassNotFoundException, IOException, SQLException {


        loadDriver();

        String companyDbUrl = String.format("jdbc:mysql://%s:%s/santaba?user=%s&password=%s", "localhost", "3306", "root", "");
        System.out.println(companyDbUrl);
        Map<String, String> company2Uuid = getCompanyUuidMap(companyDbUrl);

        Map<String, Integer> countPerCompany = new HashMap<>();
        long total = 0;
        long totalPollInterval = 0;
        for (String comp : company2Uuid.keySet()) {
            String connectionUrl = String.format("jdbc:mysql://%s:%s/%s?user=%s&password=%s", "localhost", "3306", company2Uuid.get(comp),"root", "");
//            svc.parameters as pram

            String s = "select sv.id as id, sv.parameters as param from services sv join service_checkpoint cpp on sv.id=cpp.service_id and cpp.group_id=1 and sv.parameters like '%\"isInternal\":\"true\"%' and sv.id not in (select svc.id from services svc join service_checkpoint cp on svc.id=cp.service_id and cp.group_id=3)";

            String uuid = company2Uuid.get(comp);
            String query = String.format("select count(sv.id) as count  from %s.services sv join %s.service_checkpoint cpp on sv.id=cpp.service_id and cpp.group_id=1 and sv.parameters like '%s' and sv.id not in (select svc.id from %s.services svc join %s.service_checkpoint cp on svc.id=cp.service_id and cp.group_id=3)",
                    uuid, uuid, "%\"isInternal\":\"true\"%", uuid, uuid);


            String query2 = String.format("select sv.parameters as param  from %s.services sv join %s.service_checkpoint cpp on sv.id=cpp.service_id and cpp.group_id=1 and sv.parameters like '%s' and sv.id not in (select svc.id from %s.services svc join %s.service_checkpoint cp on svc.id=cp.service_id and cp.group_id=3)",
                    uuid, uuid, "%\"isInternal\":\"true\"%", uuid, uuid);

            Connection connection = null;
            Statement statement = null;
            ResultSet resultSet = null;
            try {
                connection = DriverManager.getConnection(connectionUrl);
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query2);

                int count = 0;
                int pollingInterval = 0;
                if (!resultSet.next()) {
                }
                else {
                    do {
                        count++;
                        String param = resultSet.getString("param");
                        try {
                            JSONObject json = new JSONObject(param);
                            pollingInterval +=Integer.parseInt(json.getString("pollingInterval"));
                        }
                        catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                    while (resultSet.next());
                }
                if (count > 0) {
                    total += count;
                    totalPollInterval +=pollingInterval;
                    FileUtils.write(new File("countPerCompany.log"), String.format("%s %s %s %s %s\n", comp, uuid, count, pollingInterval, pollingInterval / count), "UTF-8", true);
                }
            }
            finally {
                closeSiliently(resultSet);
                closeSiliently(statement);
                closeSiliently(connection);
            }


        }

        if (total > 0) {
            FileUtils.write(new File("countPerCompany.log"), String.format("%s %s %s %s %s\n", "total", "-", total, totalPollInterval, totalPollInterval / total), "UTF-8", true);
        }

        System.out.println(String.format("%s %s %s %s %s\n", "total", "-", total, totalPollInterval, totalPollInterval / total));
    }



    static void statisticDeviceCount(Map<String, String> company2Uuid) throws SQLException, IOException {
        Map<String, Map<Integer, Integer>> deviceCountPerCollectorPerCompany = new HashMap<>();
        if (!company2Uuid.isEmpty()) {
            for (Map.Entry<String, String> entry : company2Uuid.entrySet()) {
                String company = entry.getKey();
                String uuid = entry.getValue();
                String connectionUrl = String.format("jdbc:mysql://%s:%s/%s?user=%s&password=%s", "localhost", "3306", uuid,"root", "");
                Map<Integer, Integer> devicePerCollector = countPerCollectorByQuery(connectionUrl, "select wildcard_groovyscript as script from datasources where collector='script'" );
                if (devicePerCollector.isEmpty()) {
                    System.err.println("No result for " + company + " " + uuid);
                    continue;
                }
                deviceCountPerCollectorPerCompany.put(company, devicePerCollector);
            }
        }


        File file = new File("instance_count_statistics.txt");
        String format = "%20s %20s %10d %10d\n";
        for (String company : deviceCountPerCollectorPerCompany.keySet()) {
            String uuid = company2Uuid.get(company);
            Map<Integer, Integer> instanceCount = deviceCountPerCollectorPerCompany.get(company);
            for (Map.Entry<Integer, Integer> count : instanceCount.entrySet()) {
                FileUtils.write(file, String.format(format, company, uuid, count.getKey(), count.getValue()),"UTF-8", true);
            }
        }

    }

    /**
     * Statistic instance counts and save toinstance_count_statistics.txt
     * @param company2Uuid
     * @throws IOException
     */
    static void statisticInstanceCounts(Map<String, String> company2Uuid ) throws IOException, SQLException {
        Map<String, Map<Integer, Integer>> instanceCountPerCollectorPerCompany = new HashMap<>();
        if (!company2Uuid.isEmpty()) {
            for (Map.Entry<String, String> entry : company2Uuid.entrySet()) {
                String company = entry.getKey();
                String uuid = entry.getValue();
                String connectionUrl = String.format("jdbc:mysql://%s:%s/%s?user=%s&password=%s", "localhost", "3306", uuid,"root", "");
                Map<Integer, Integer> instancePerCollector = countPerCollectorByQuery(connectionUrl,  "select h.agent_id as id, count(i.id) as count from hosts h inner join datasourceinstances i on h.id = i.host_id where i.enable!='n' and h.agent_id > 0 group by h.agent_id");
                if (instancePerCollector.isEmpty()) {
                    System.err.println("No result for " + company + " " + uuid);
                    continue;
                }
                instanceCountPerCollectorPerCompany.put(company, instancePerCollector);
            }
        }

        File file = new File("instance_count_statistics.txt");
        String format = "%20s %20s %10d %10d\n";
        for (String company : instanceCountPerCollectorPerCompany.keySet()) {
            String uuid = company2Uuid.get(company);
            Map<Integer, Integer> instanceCount = instanceCountPerCollectorPerCompany.get(company);
            for (Map.Entry<Integer, Integer> count : instanceCount.entrySet()) {
                FileUtils.write(file, String.format(format, company, uuid, count.getKey(), count.getValue()),"UTF-8", true);
            }
        }
    }

    static Map<String, String> getCompanyUuidMap(String companyDbUrl) throws SQLException {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        Map<String, String> company2Uuid = new HashMap<>();
        try {
            connection = DriverManager.getConnection(companyDbUrl);
            String query = "select c.db_name as uuid, c.name as name from companies c where c.company_status='active'";
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);


            if (!resultSet.next()) {
                System.err.println("No results for companies");
            }
            else {
                do {
                    String company = resultSet.getString("name");
                    String uuid = resultSet.getString("uuid");
                    company2Uuid.put(company, uuid);
                } while (resultSet.next());

            }

        }
        finally {
            closeSiliently(resultSet);
            closeSiliently(statement);
            closeSiliently(connection);
        }

        return company2Uuid;
    }



    static Map<Integer/*AgentId*/, Integer/*Count*/> countPerCollectorByQuery(String dbUrl, String query) throws SQLException {
        // select h.agent_id, count(i.id) from hosts h inner join datasourceinstances i on h.id = i.host_id where i.enable!='n' group by h.agent_id;
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        Map<Integer, Integer> instanceCountPerCollector = new HashMap<>();
        try {
            connection = DriverManager.getConnection(dbUrl);
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);

            if (!resultSet.next()) {
            }
            else {
                do {
                    Integer agentId = resultSet.getInt("id");
                    Integer count = resultSet.getInt("count");
                    instanceCountPerCollector.put(agentId, count);
                }
                while (resultSet.next());
            }
        }
        finally {
            closeSiliently(resultSet);
            closeSiliently(statement);
            closeSiliently(connection);
        }

        return instanceCountPerCollector;
    }

    private static void closeSiliently(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        }
        catch (Exception ignore) {
        }
    }

    private static void loadDriver() throws ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
    }
}
