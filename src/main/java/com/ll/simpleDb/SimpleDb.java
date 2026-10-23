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

    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>(); // 스레드마다 별도의 값을 저장할 수 있는 도구

    Connection getConnection() throws SQLException {
        Connection conn = transactionConnection.get();

        // 트랜잭션이 진행 중이라면 기존 Connection 재사용
        if (conn != null) {
            return conn;
        }

        // 트랜잭션이 없다면 새로운 Connection 생성
        return createConnection();
    }

    // sql이 connection을 직접 닫아도 되는지 판단
    boolean isInTransaction() {
        return transactionConnection.get() != null;
    }

    // 트랜잭션 중이면 connection 유지 / 트랜잭션 아니면 connection 종료
    void closeConnectionIfNeeded(Connection conn) {
        if (conn == null) {
            return;
        }

        if (!isInTransaction()) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException("Connection 종료 실패", e);
            }
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

        if (devMode) {
            System.out.println("SQL: " +  sql);
        }
        Connection connection = getConnection();
        try (
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
        finally {
            closeConnectionIfNeeded(connection);
        }
    }

    public void close() {
        Connection conn = transactionConnection.get();

        if (conn == null) {
            return;
        }

        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException("Connection 종료 실패", e);
        } finally {
            transactionConnection.remove();
        }
    }

    public void startTransaction() throws SQLException {
        if (isInTransaction()) throw new IllegalStateException("이미 트랜잭션이 진행 중입니다.");

        Connection conn = createConnection();

        try {
            conn.setAutoCommit(false); // 직접 commit 호출 전까진 변경사항 확정 x
            transactionConnection.set(conn);
        } catch (SQLException e) {
            try {
                conn.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }

            throw new RuntimeException("트랜잭션 시작 실패", e);
        }

    }

    public void commit() {
        Connection conn = transactionConnection.get();

        if (conn == null) {
            throw new IllegalStateException("진행 중인 트랜잭션이 없습니다.");
        }

        try {
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("커밋 실패", e);
        } finally {
            close();
        }
    }

    public void rollback() {
        Connection conn = transactionConnection.get();

        if (conn == null) {
            throw new IllegalStateException("진행 중인 트랜잭션이 없습니다.");
        }

        try {
            conn.rollback();
        } catch (SQLException e) {
            throw new RuntimeException("롤백 실패", e);
        } finally {
            close();
        }
    }





}
