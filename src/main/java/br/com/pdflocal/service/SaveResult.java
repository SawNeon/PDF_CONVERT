package br.com.pdflocal.service;

public record SaveResult(int pages, long outputBytes, CompressionStats compression) {
}
