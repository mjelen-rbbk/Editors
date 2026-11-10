/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package blockwoche;

import java.sql.*;

/**
 *
 * @author info
 */
public class DataBase implements AutoCloseable {

    public String driver = "org.postgresql.Driver";
    public String url = "jdbc:postgresql://localhost:5432/";
    public String usr = "postgres";
    public String passwd = "admin";

    private Connection con;
    private final String connectionUrl;

    public DataBase(String dbName) {
        this.connectionUrl = url + dbName;

        // Treiber laden wenn erforderlich
        try { // kann unter JDK 8 entfallen
            Class.forName(driver);
            con = DriverManager.getConnection(connectionUrl, usr, passwd);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("Fehler: " + e + " beim Verbindungsaufbau.");
        }
    }

    public Connection getConnection() {
        try {
            if (!con.isValid(0)) { // Verbindung ist nicht mehr gueltig
                con = DriverManager.getConnection(connectionUrl, usr, passwd);
            }
        } catch (SQLException e) {
            System.err.println("Fehler: " + e + " beim Verbindungsaufbau.");
        }

        return con;
    }

    @Override
    public void close() {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                System.err.println("Fehler: " + e + " beim Schliessen der Verbindung.");
            } finally {
                con = null;
            }
        }
    }
}
