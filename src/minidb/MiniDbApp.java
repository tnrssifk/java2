package minidb;

import java.util.Scanner;

/**
 * Консольное приложение MiniDB
 */
public class MiniDbApp {
    public static void main(String[] args) {
        DatabaseEngine engine = new DatabaseEngine();
        QueryParser parser = new QueryParser(engine);
        Scanner sc = new Scanner(System.in);

        System.out.println("=== MiniDB Console ===");
        System.out.println("Введите SQL-запрос или 'exit' для выхода.");
        System.out.println("Пример: CREATE TABLE cars (id INT PK, brand STRING, price DOUBLE);");

        while (true) {
            System.out.print("mini-db> ");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("exit")) break;
            if (line.isEmpty()) continue;

            String result = parser.execute(line);
            System.out.println(result);
        }
        sc.close();
        System.out.println("Данные сохранены. До свидания!");
    }
}