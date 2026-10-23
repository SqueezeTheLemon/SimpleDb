// SQL 조립, 파라미터(?) 바인딩, 결과 변환
//append(sqlPart, params...) — ? 바인딩 포함
//appendIn(sqlPart, params...) — IN (?) 의 ? 를 값 개수만큼 펼침
//insert() → 생성된 주키, update()/delete() → 영향받은 행 수
//selectRows() → List<Map<String, Object>>, selectRow() → Map<String, Object>
//selectRows(Article.class) / selectRow(Article.class) — 결과를 클래스에 매핑
//selectDatetime(), selectLong(), selectLongs(), selectString(), selectBoolean()

package com.ll.simpleDb;

import java.sql.*;
import java.util.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Sql {

    private final SimpleDb simpleDb; // DB 연결을 얻기 위한 객체
    private final StringBuilder sqlBuilder = new StringBuilder(); // sql 문자열 누적 - 계속 연결하기 위한 특별한 string+@
    private final List<Object> params = new ArrayList<>(); // ?에 바인딩할 값들을 저장할 배열

    public Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
    }

    // 제네릭 사용 -  article, member class 중 뭘 전달해도 각 객체를 반환한다.
    private <T> T mapToObject(Map<String, Object> row, Class<T> clazz) {
        try{
            // 1. 전달받은 클래스의 기본 생성자 가져오기
            Constructor <T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);

            // 2. 새로운 객체 생성 (T type)
            T object = constructor.newInstance();

            // 3. Map의 컬럼명과 값을 하나씩 확인
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();

                // 4. 같은 이름의 필드 찾기
                Field field = clazz.getDeclaredField(fieldName); // DB 칼럼 이름과 동일한 이름의 필드를 찾는ㄴ다.
                field.setAccessible(true);

                // 5. 타입 변환
                if(value instanceof Number number) {
                    if (field.getType() == long.class || field.getType() == Long.class) value = number.longValue();
                    else if (field.getType() == int.class || field.getType() == Integer.class)  value = number.intValue();
                }

                // 6. 객체 필드에 값 저장
                field.set(object, value);
            }
            return object;

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("객체 매핑 실패: " + clazz.getName(), e);
        }

    }

    // SQL 문자열을 누적해주는 append 함수 (문자열 조립)
    public Sql append(String sqlPart, Object... values) throws SQLException {
        if(!sqlBuilder.isEmpty()){
            sqlBuilder.append(" ");
        }
        sqlBuilder.append(sqlPart);

        for (Object value : values){
            params.add(value);
        }

        return this; // 현재 Sql 객체
    }

    public Sql appendIn(String sqlPart, Object... values) throws SQLException {
        if(values.length == 0) throw new IllegalArgumentException("IN 조건에는 값이 하나 이상 필요합니다.");

        // 전달된 값 개수(value.length)만큼 ? 생성
        String placeholders = String.join(", ", java.util.Collections.nCopies(values.length, "?"));

        // 기존 ?를 확장된 ? 목록으로 변경
        String expandedSql = sqlPart.replaceFirst("\\?", java.util.regex.Matcher.quoteReplacement(placeholders));

        // 기존 append 재사용하기
        return append(expandedSql, values);
    }

    // 공통된 "sql실행" 작업
    private int executeUpdate() throws SQLException {
        String sql = sqlBuilder.toString();
        simpleDb.printSql(sql);

        try(
                Connection conn = simpleDb.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
                ){
            for(int i = 0; i < params.size(); i++){
                pstmt.setObject(i + 1, params.get(i));
            }
            return pstmt.executeUpdate();
        }
        catch (SQLException e){
            throw new RuntimeException("SQL 실행 실패", e);
        }
    }

    public int update() throws SQLException {
        return executeUpdate();
    }

    public int delete() throws SQLException {
        return executeUpdate();
    }

    // 자동 생성된 id를 가져오기 - 데이터 추가 및 id 반환
    public long insert() throws SQLException {
        String sql = sqlBuilder.toString();
        System.out.println("실행 SQL: [" + sql + "]");
        System.out.println("파라미터: " + params);

        simpleDb.printSql(sql);

        try(
                Connection conn = simpleDb.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                ){
            for(int i = 0; i < params.size(); i++){
                pstmt.setObject(i + 1, params.get(i));
            }
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()){
                if(rs.next()){
                    return rs.getLong(1);
                }
            }

            throw new IllegalStateException("생성된 ID가 없습니다.");

        }
        catch (SQLException e){
            throw new RuntimeException("INSERT 실행 실패! ", e);
        }

    }

    public List<Map<String, Object>> selectRows(){
        // 1. 지금까지 조립한 sql 가져오기
        String sql = sqlBuilder.toString();
        simpleDb.printSql(sql);

        // 2. ResultSet(최종 결과를 저장할 리스트) 생성
        List <Map<String, Object>> rows = new ArrayList<>();


        try(
                Connection conn = simpleDb.createConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql);

                ) {
            // 3. 파라미터 바인딩
            for (int i=0; i<params.size(); i++){
                pstmt.setObject(i + 1, params.get(i));
            }

            // 4. Select 실행
            try(ResultSet rs = pstmt.executeQuery()){

                // 5. 조회 결과의 컬럼 정보 가져오기
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount(); // 조회 결과의 컬럼 개수 구하기

                // 6. 조회된 모든 행 순회
                while (rs.next()){ // 행을 하나씩 순회
                    Map<String, Object> row = new LinkedHashMap<>();

                    // 7. 현재 행의 모든 컬럼 하나씩 순회 -
                    for (int i = 1; i <= columnCount; i++){
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);

                        // DATETIME -> LocalDateTime
                        if (value instanceof Timestamp timestamp){
                            value = timestamp.toLocalDateTime();
                        }
                        row.put(columnName, value);
                    }
                    // 완성된 행을 리스트에 추가하기
                    rows.add(row);
                }
            }
            return rows;

        } catch (SQLException e) {
            throw new RuntimeException("SELECT 실행 실패!", e);
        }
    }

    public Map<String, Object> selectRow(){
        // selectRows() 결과에서
        List <Map<String, Object>> rows = selectRows();
        if(rows.isEmpty()) return null;

        // 첫번째 행만 가져온다.
        return rows.get(0);
    }

    public <T> T selectRow(Class<T> clazz){
        Map<String, Object> row = selectRow();

        if(row == null) return null;
        return mapToObject(row, clazz);
    }

    public <T> List<T> selectRows(Class<T> clazz){
        List<Map<String, Object>> rows = selectRows();
        List<T> result = new ArrayList<>();

        for(Map<String, Object> row : rows){
            T object = mapToObject(row, clazz);
            result.add(object);
        }

        return result;
    }

    // SELECT 결과의 첫번째 행, 첫번째 컬럼 값을 반환
    private Object selectScalar(){
        Map<String, Object> row = selectRow();

        if(row == null || row.isEmpty()) return null;

        // Map에 저장된 값들만 가져와서, 첫번째 값을 선택한다.  - LinkedHashMap을 사용할 때만 가능
        return row.values().iterator().next();
    }

    // 첫번째 행 (selectScalar)에서 숫자 하나씩만 가져오기
    public long selectLong(){
        Object value = selectScalar();
        return ((Number)value).longValue(); // DB에서 변환한 값이 항상 long임을 확신 x, Number를 사용한다.
    }

    public List<Long> selectLongs(){
        List <Map<String, Object>> rows = selectRows();
        List <Long> result = new ArrayList<>();

        for(Map<String, Object> row : rows){
            Object value = row.values().iterator().next();

            result.add(((Number) value).longValue());
        }

        return result;
    }

    public String selectString(){
        Object value = selectScalar();

        if(value == null) return null;
        return value.toString(); // 문자열 객체가 아니어도 문자열 형태로 변환한다.
    }

    public LocalDateTime selectDatetime(){
        Object value = selectScalar();

        if(value == null) return null;

        if (value instanceof LocalDateTime dateTime) return dateTime;

        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();

        throw new IllegalStateException("LocalDateTime으로 변환할 수 없는 값입니다: " + value);
    }

    public boolean selectBoolean(){
        Object value = selectScalar();

        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue()!=0; // mySql이 1또는 0을 숫자로 반환했다면 참거짓 판단
        throw new IllegalStateException("Boolean으로 변환할 수 없는 값입니다: " + value);
    }




}
