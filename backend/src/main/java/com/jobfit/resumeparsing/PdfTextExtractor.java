package com.jobfit.resumeparsing;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Thin wrapper around Apache PDFBox. Multi-column layouts, tables, and
 * heavily designed resume templates can still produce text extraction
 * artifacts (columns interleaved, spacing collapsed) - PDFBox gives a
 * reasonable best-effort linear text stream, and the mandatory user
 * review/edit step downstream (ResumeController PATCH endpoint) is what
 * actually guarantees correctness, not this step alone.
 */
@Component
public class PdfTextExtractor {

    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new UnparsableFileException("This PDF is password-protected. Please upload an unprotected file.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new UnparsableFileException(
                        "No extractable text was found in this PDF. It may be a scanned image - " +
                                "text-based PDFs only are supported for now.");
            }
            return text;
        } catch (IOException e) {
            throw new UnparsableFileException("Could not read this file as a PDF.", e);
        }
    }
}
