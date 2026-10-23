// 만들어야 할 것들 : JDBC 연결, 트랜잭션, 커넥션 관리
//new SimpleDb(host, username, password, dbName), setDevMode(boolean)(개발 모드면 실행 SQL 출력)
//run(sql, params...) — DDL/DML 실행
//genSql() — Sql 객체 생성
//startTransaction(), commit(), rollback(), close()


package com.ll.simpleDb;

import lombok.Setter;

import java.sql.Connection;
import java.sql.DriverManager; //DB 연결을 생성함
import java.sql.PreparedStatement; // sql 준비 및 파라미터 바인딩
import java.sql.SQLException;

public class SimpleDb {
    private final String host;
    private final String user;
    private final String password;
    private final String dbName;

    // 추후 run()에서 사용 - 개발모드 활성화 (실행할 sql을 콘솔에 출력)
    // 개발 모드면 실행 sql을 출력한다.
    @Setter
    private boolean devMode = false;

    // 연결에 필요한 정보를 객체 (SimpleDb)에 보관
    public SimpleDb(String host, String user, String password, String dbName) {
        this.host = host;
        this.user = user;
        this.password = password;
        this.dbName = dbName;
    }

    // connection 생성
    Connection createConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":3306/" + dbName;

        try{
            return DriverManager.getConnection(url, user, password); //mysql에 연결을 요청함
        } catch (SQLException e) {
            throw new RuntimeException("DB 연결 실패! ", e);
        }
    }

    // SQL 객체 생성
    public Sql genSql() {
        return new Sql(this);
    }

    // 개발 모드 출력
    void printSql(String sql) {
        if (devMode) {
            System.out.println("SQL: " + sql);
        }
    }





    public void run(String sql, Object... params) throws SQLException {
        System.out.println("[SimpleDb.run] " + sql);

        if (devMode) {
            System.out.println("SQL: " +  sql);
        }

        try (
                Connection connection = createConnection();
                PreparedStatement pstmt = connection.prepareStatement(sql);
        ){

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i+1, params[i ]);
            }

            pstmt.executeUpdate();
        }
        catch (SQLException e){
            throw new RuntimeException("SQL 실행 실패!", e);
        }
    }


}
