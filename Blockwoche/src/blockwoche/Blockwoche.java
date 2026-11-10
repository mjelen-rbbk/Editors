/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package blockwoche;

import java.sql.*;

/**
 *
 * @author info
 */
public class Blockwoche {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String sql = "";
        
        try (DataBase db = new DataBase("test")) {
            Connection con = db.getConnection();
            try (Statement stmt = con.createStatement()) {
                sql = "SELECT * FROM Cities";
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        System.out.println("Name: " + rs.getString(2));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Fehler " + e + " bei der Anweisung:\n" + sql);
        }
    }
}
