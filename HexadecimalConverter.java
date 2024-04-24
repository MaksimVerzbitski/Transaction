package com.playtech.assignment;
import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class HexadecimalConverter {

    public static void main(String[] args) throws IOException {
        // Specify the paths to the input CSV files
        String[] inputFiles = {
                "D:\\Projects\\Transation\\test-data\\test random data (small)\\input\\users.csv",
                "D:\\Projects\\Transation\\test-data\\test random data (small)\\input\\transactions.csv",
                "D:\\Projects\\Transation\\test-data\\test random data (small)\\input\\bins.csv",
                "D:\\Projects\\Transation\\test-data\\test random data (small)\\output example\\balances.csv",
                "D:\\Projects\\Transation\\test-data\\test random data (small)\\output example\\events.csv"
        };

        // Specify the column indices that may contain hexadecimal values
        // For transactions.csv, assuming account_number is the 5th column (index 4)
        int[] hexColumnIndices = {0,1}; // Adjust this based on actual column indices


        // Process each file
        for (String inputFile : inputFiles) {
            List<String[]> data = readCsv(inputFile);
            convertHexToDecimal(data, hexColumnIndices);
            writeCsv(inputFile, data);
        }
    }

    private static List<String[]> readCsv(String filePath) throws IOException {
        List<String[]> data = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                data.add(line.split(","));
            }
        }
        return data;
    }

    private static void convertHexToDecimal(List<String[]> data, int[] hexColumnIndices) {
        for (String[] row : data) {
            for (int columnIndex : hexColumnIndices) {
                if (columnIndex < row.length) {
                    String value = row[columnIndex];
                    if (isHexadecimal(value)) {
                        row[columnIndex] = new BigInteger(value, 16).toString(10);
                    }
                }
            }
        }
    }

    private static boolean isHexadecimal(String value) {
        return value.matches("\\p{XDigit}+");
    }

    private static void writeCsv(String filePath, List<String[]> data) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            for (String[] row : data) {
                bw.write(String.join(",", row));
                bw.newLine();
            }
        }
    }
}
