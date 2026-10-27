package in.edu.bennett.capstone.knowledge;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Fixture PDFs generated in code with known text on known pages, so no binary files are committed
 * (specification 19.1).
 */
public final class TestPdfs {

  private TestPdfs() {}

  /** One line of text per page, in order. */
  public static byte[] withPages(String... pageTexts) {
    try (PDDocument pdf = document(pageTexts)) {
      return bytes(pdf);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Several lines per page, top to bottom; an empty list gives a page without any text. Every call
   * also writes a unique line at the end of the last page, so two uploads are never duplicates.
   */
  public static byte[] withPageLines(List<List<String>> pages) {
    try (PDDocument pdf = new PDDocument()) {
      for (int p = 0; p < pages.size(); p++) {
        PDPage page = new PDPage();
        pdf.addPage(page);
        List<String> lines = new ArrayList<>(pages.get(p));
        if (p == pages.size() - 1) {
          lines.add("Fixture " + UUID.randomUUID());
        }
        if (lines.isEmpty()) {
          continue;
        }
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
          content.beginText();
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
          content.setLeading(13);
          content.newLineAtOffset(50, 740);
          for (String line : lines) {
            content.showText(line);
            content.newLine();
          }
          content.endText();
        }
      }
      return bytes(pdf);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A scanned page: an image and no text layer (ING-4). */
  public static byte[] imageOnly(int pageCount) {
    try (PDDocument pdf = new PDDocument()) {
      for (int p = 0; p < pageCount; p++) {
        PDPage page = new PDPage();
        pdf.addPage(page);
        BufferedImage scan = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scan.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 200, 100);
        graphics.setColor(Color.BLACK);
        graphics.drawString("Scanned text " + UUID.randomUUID(), 5, 50);
        graphics.dispose();
        PDImageXObject image = LosslessFactory.createFromImage(pdf, scan);
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
          content.drawImage(image, 50, 500, 400, 200);
        }
      }
      return bytes(pdf);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * A one-page PDF whose content differs from every other call, so the duplicate rule (DOC-2) does
   * not see two tests' uploads as the same file.
   */
  public static byte[] unique() {
    return withPages("Test document " + UUID.randomUUID());
  }

  /** A PDF that cannot be opened without the given user password. */
  public static byte[] encrypted(String userPassword) {
    return protectedPdf("owner-" + UUID.randomUUID(), userPassword);
  }

  /** A PDF with an owner password only: it restricts permissions but opens without a password. */
  public static byte[] ownerPasswordOnly() {
    return protectedPdf("owner-" + UUID.randomUUID(), "");
  }

  private static byte[] protectedPdf(String ownerPassword, String userPassword) {
    try (PDDocument pdf = document("Protected " + UUID.randomUUID())) {
      StandardProtectionPolicy policy =
          new StandardProtectionPolicy(ownerPassword, userPassword, new AccessPermission());
      policy.setEncryptionKeyLength(256);
      pdf.protect(policy);
      return bytes(pdf);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static PDDocument document(String... pageTexts) {
    PDDocument pdf = new PDDocument();
    try {
      for (String text : pageTexts) {
        PDPage page = new PDPage();
        pdf.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
          content.beginText();
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
          content.newLineAtOffset(72, 700);
          content.showText(text);
          content.endText();
        }
      }
      return pdf;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] bytes(PDDocument pdf) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      pdf.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
