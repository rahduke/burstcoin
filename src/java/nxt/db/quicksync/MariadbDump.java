package nxt.db.quicksync;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import nxt.Constants;
import nxt.Nxt;
import nxt.db.sql.Db;
import nxt.util.LoggerConfigurator;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.GZIPOutputStream;


public class MariadbDump {
    private static final Logger logger = LoggerFactory.getLogger(MariadbDump.class.getSimpleName());

    public static void main(String[] args) {
        try {
            long startTime = System.currentTimeMillis();

            LoggerConfigurator.init();

            String dbUrl;
            if (Constants.isTestnet) {
                dbUrl = Nxt.getStringProperty("nxt.testDbUrl");
            } else {
                dbUrl = Nxt.getStringProperty("nxt.dbUrl");
            }

            Db.init();
            dump(args[0]);
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    public static void dump(String filename) throws IOException, URISyntaxException, ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {

        Kryo kryo = new Kryo();

        try (Output output = new Output(new GZIPOutputStream(new FileOutputStream(filename)))) {
            try (Connection con = Db.getConnection()) {
                Db.beginTransaction();
                List<String> classes = getClassNamesFromPackage("nxt.db.quicksync.pojo");
                for (String classname : classes) {
                    Class clazz = Class.forName("nxt.db.quicksync.pojo." + classname);
                    StringBuilder sb = new StringBuilder("select ");
                    List<Field> fields = new ArrayList<>();
                    for (Field field : ReflectionUtils.getAllFields(clazz)) {
                        fields.add(field);
                        sb.append(field.getName()).append(",");
                    }
                    // Remove last ,
                    sb.deleteCharAt(sb.lastIndexOf(","));
                    sb.append(" from ");
                    sb.append(clazz.getSimpleName().toLowerCase());
                    sb.append(" limit :from,50000;");
                    String sql = sb.toString();
                    System.out.println(sql);
                    kryo.writeClass(output,clazz);
                    ResultSet rs = con.createStatement().executeQuery("select count(1) from " + classname);
                    rs.next();
                    long rows = rs.getLong(1);
                    output.writeLong(rows);
                    long records = 0;
                    while (records < rows) {
                        String sqlToExecute = sql.replaceAll(":from", String.valueOf(records));
                        PreparedStatement ps = con.prepareStatement(sqlToExecute);
                        rs = ps.executeQuery();
                        Object data = clazz.newInstance();
                        while (rs.next()) {
                            records++;
                            if (records % 1000 == 0)
                                System.err.println(classname + ": " + records + " / " + rows);
                            int i = 1;


                            for (Field field : fields) {
                                Class fieldType = field.getType();
                                field.setAccessible(true);
                                Object value;


                                if (fieldType.equals(String.class)) {
                                    value = rs.getString(i);
                                } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
                                    value = rs.getLong(i);
                                } else if (fieldType.isArray()) {
                                    // Byte array?
                                    if (fieldType.getComponentType().equals(byte.class)) {
                                        // Not sure if this works across drivers
                                        value = rs.getBytes(i);
                                    } else {
                                        System.err.println(field.getName() + ": " + fieldType);
                                        value = rs.getObject(i);
                                    }

                                } else {
                                    System.err.println(field.getName() + ": " + fieldType);
                                    value = rs.getObject(i);
                                }
                                field.set(data, value);
                                i++;
                            }

                            kryo.writeObject(output, data);
                        }
                        rs.close();
                        ps.close();
                        output.flush();
                    }


                }
                Db.endTransaction();
            }

        }
    }


    public static List<String> getClassNamesFromPackage(String packageName) throws IOException, URISyntaxException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL packageURL;
        ArrayList<String> names = new ArrayList<String>();


        packageName = packageName.replace(".", "/");
        packageURL = classLoader.getResource(packageName);

        if (packageURL.getProtocol().equals("jar")) {
            String jarFileName;
            JarFile jf;
            Enumeration<JarEntry> jarEntries;
            String entryName;

            // build jar file name, then loop through zipped entries
            jarFileName = URLDecoder.decode(packageURL.getFile(), "UTF-8");
            jarFileName = jarFileName.substring(5, jarFileName.indexOf("!"));
            jf = new JarFile(jarFileName);
            jarEntries = jf.entries();
            while (jarEntries.hasMoreElements()) {
                entryName = jarEntries.nextElement().getName();
                if (entryName.startsWith(packageName) && entryName.length() > packageName.length() + 5) {
                    entryName = entryName.substring(packageName.length(), entryName.lastIndexOf('.'));
                    names.add(entryName);
                }
            }

            // loop through files in classpath
        } else {
            URI uri = new URI(packageURL.toString());
            File folder = new File(uri.getPath());
            // won't work with path which contains blank (%20)
            // File folder = new File(packageURL.getFile());
            File[] contenuti = folder.listFiles();
            String entryName;
            for (File actual : contenuti) {
                entryName = actual.getName();
                if (entryName.contains(".")) {
                    entryName = entryName.substring(0, entryName.lastIndexOf('.'));
                    names.add(entryName);
                }
            }
        }
        return names;
    }
}
