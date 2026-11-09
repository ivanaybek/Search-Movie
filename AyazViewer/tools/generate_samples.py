#!/usr/bin/env python3
"""Ayaz Viewer test dosyalarını üretir.

Kullanım:  python3 tools/generate_samples.py
Gereksinim: openpyxl, reportlab  (pip install openpyxl reportlab)

Üretilen dosyalar:
  xlsx/src/test/resources/          -> JVM birim testleri (XLSX motoru)
  app/src/androidTest/assets/       -> cihaz/emülatör testleri (PDF + XLSX)
  samples/                          -> telefonda elle denemek için kopyalar
"""
import datetime as dt
import os
import shutil
import zipfile

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.styles.colors import Color
from reportlab.lib.pagesizes import A4, A5, landscape
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JVM_RES = os.path.join(ROOT, "xlsx", "src", "test", "resources")
ANDROID_ASSETS = os.path.join(ROOT, "app", "src", "androidTest", "assets")
SAMPLES = os.path.join(ROOT, "samples")

TURKISH = "Şükrü Çağlayan – İstanbul, Iğdır; ğüşıöç ĞÜŞİÖÇ"


def ensure_dirs():
    for d in (JVM_RES, ANDROID_ASSETS, SAMPLES):
        os.makedirs(d, exist_ok=True)


# ----------------------------------------------------------------------------- XLSX

def simple_xlsx(path):
    wb = Workbook()
    ws = wb.active
    ws.title = "Satışlar"
    header_fill = PatternFill("solid", fgColor="1F4E79")
    header_font = Font(bold=True, color="FFFFFF")
    thin = Side(style="thin", color="999999")
    headers = ["Ürün", "Adet", "Birim Fiyat", "Tarih", "Oran", "Not"]
    for c, h in enumerate(headers, start=1):
        cell = ws.cell(row=1, column=c, value=h)
        cell.fill = header_fill
        cell.font = header_font
        cell.alignment = Alignment(horizontal="center")
        cell.border = Border(bottom=thin)
    rows = [
        ("Çay", 12, 1234.5, dt.date(2024, 3, 5), 0.125, TURKISH),
        ("Şeker", 3, 99.99, dt.date(2023, 12, 31), 0.5, "Özel not"),
        ("Iğdır kayısısı", 1500000, 0.75, dt.datetime(2024, 1, 15, 14, 30, 0), 1, ""),
        ("Negatif", -42, -1234.567, dt.date(2000, 2, 29), -0.033, None),
    ]
    for r, row in enumerate(rows, start=2):
        for c, v in enumerate(row, start=1):
            if v is None:
                continue
            cell = ws.cell(row=r, column=c, value=v)
            if c == 2:
                cell.number_format = "#,##0"
            elif c == 3:
                cell.number_format = '#,##0.00 "₺"'
            elif c == 4:
                cell.number_format = "dd.mm.yyyy hh:mm" if isinstance(v, dt.datetime) else "dd.mm.yyyy"
            elif c == 5:
                cell.number_format = "0.0%"
    ws["A7"] = "Kalın İtalik"
    ws["A7"].font = Font(bold=True, italic=True, color="C00000")
    ws["B7"] = 0.000012345
    ws["C7"] = 123456789012
    ws["D7"] = True
    ws["E7"] = 1 / 3
    ws["E7"].number_format = "General"
    ws["F7"] = 5.0
    ws["F7"].number_format = "0.00E+00"
    ws.column_dimensions["A"].width = 22
    ws.column_dimensions["F"].width = 40
    ws.row_dimensions[1].height = 24
    ws.freeze_panes = "B2"
    wb.save(path)


def multi_sheet_xlsx(path):
    wb = Workbook()
    s1 = wb.active
    s1.title = "Özet"
    s1["A1"] = "Toplam"
    s1["B1"] = 1000
    s2 = wb.create_sheet("Veriler")
    for r in range(1, 201):
        for c in range(1, 11):
            s2.cell(row=r, column=c, value=r * c)
    s2["A201"] = "Son satır"
    wb.create_sheet("Boş Sayfa")
    hidden = wb.create_sheet("Gizli")
    hidden["A1"] = "gizli"
    hidden.sheet_state = "hidden"
    s5 = wb.create_sheet("Birleşik")
    s5["A1"] = "Başlık (A1:D1 birleşik)"
    s5.merge_cells("A1:D1")
    s5["A1"].alignment = Alignment(horizontal="center", vertical="center")
    s5["B3"] = "B3:C5"
    s5.merge_cells("B3:C5")
    s5["A3"].fill = PatternFill("solid", fgColor=Color(theme=4, tint=0.6))
    s5["F10"] = "uzak hücre"
    s5.column_dimensions["E"].hidden = True
    s5.sheet_properties.tabColor = "FF0000"
    wb.save(path)


def write_zip(path, files):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, content in files.items():
            z.writestr(name, content)


CT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
</Types>"""

ROOT_RELS = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""


def handcrafted_xlsx(path):
    """El yazımı XML: satır içi dizeler, r özniteliği olmayan hücreler, önbellekli formüller,
    zengin metin, kaçış dizileri, 1904 tarih sistemi, mutlak hedef yolları, ad alanı öneki."""
    workbook = """<?xml version="1.0" encoding="UTF-8"?>
<x:workbook xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<x:workbookPr date1904="1"/>
<x:sheets><x:sheet name="Elle Yazılmış" sheetId="1" r:id="rId1"/><x:sheet name="Grafik" sheetId="2" r:id="rId9"/></x:sheets>
</x:workbook>"""
    rels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="/xl/worksheets/sheet1.xml"/>
<Relationship Id="rId9" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chartsheet" Target="chartsheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""
    sst = """<?xml version="1.0" encoding="UTF-8"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
<si><t>düz metin</t></si>
<si><r><rPr><b/></rPr><t xml:space="preserve">Zengin </t></r><r><t>metin</t></r><rPh sb="0" eb="1"><t>FONETIK</t></rPh></si>
<si><t>satır_x000D_sonu</t></si>
</sst>"""
    sheet = """<?xml version="1.0" encoding="UTF-8"?>
<x:worksheet xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<x:sheetData>
<x:row r="1">
  <x:c t="s"><x:v>0</x:v></x:c>
  <x:c t="s"><x:v>1</x:v></x:c>
  <x:c t="s"><x:v>2</x:v></x:c>
</x:row>
<x:row>
  <x:c t="inlineStr"><x:is><x:t>satır içi ğüş</x:t></x:is></x:c>
  <x:c><x:f>1+1</x:f><x:v>2</x:v></x:c>
  <x:c t="str"><x:f>"a"&amp;"b"</x:f><x:v>ab</x:v></x:c>
  <x:c t="b"><x:v>1</x:v></x:c>
  <x:c t="e"><x:f>1/0</x:f><x:v>#DIV/0!</x:v></x:c>
</x:row>
<x:row r="5">
  <x:c r="C5" t="d"><x:v>2024-02-29T13:45:00</x:v></x:c>
  <x:c r="A5"><x:v>0</x:v></x:c>
  <x:c r="B5" t="s"><x:v>999</x:v></x:c>
</x:row>
</x:sheetData>
</x:worksheet>"""
    write_zip(path, {
        "[Content_Types].xml": CT,
        "_rels/.rels": ROOT_RELS,
        "xl/workbook.xml": workbook,
        "xl/_rels/workbook.xml.rels": rels,
        "xl/sharedStrings.xml": sst,
        "xl/worksheets/sheet1.xml": sheet,
    })


def formulas_xlsx(path):
    """Düzenleme testleri için: formüller, paylaşılan formül, birleşik hücre, koşullu biçim,
    veri doğrulama, köprü, sütun genişlikleri, calcChain ve ikinci sayfa."""
    main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    ct = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
<Override PartName="/xl/calcChain.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.calcChain+xml"/>
</Types>"""
    workbook = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="{main}" xmlns:r="{rel}">
<sheets><sheet name="Formüller" sheetId="1" r:id="rId1"/><sheet name="Diğer Sayfa" sheetId="2" r:id="rId2"/></sheets>
<calcPr calcId="191029"/>
</workbook>"""
    wb_rels = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="{rel}/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="{rel}/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="{rel}/styles" Target="styles.xml"/>
<Relationship Id="rId4" Type="{rel}/sharedStrings" Target="sharedStrings.xml"/>
<Relationship Id="rId5" Type="{rel}/calcChain" Target="calcChain.xml"/>
</Relationships>"""
    styles = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="{main}">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
<dxfs count="1"><dxf><font><b/></font></dxf></dxfs>
</styleSheet>"""
    sst = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="{main}" count="1" uniqueCount="1"><si><t>Birleşik</t></si></sst>"""
    sheet1 = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="{main}" xmlns:r="{rel}">
<dimension ref="A1:F5"/>
<sheetViews><sheetView workbookViewId="0"/></sheetViews>
<sheetFormatPr defaultRowHeight="15"/>
<cols><col min="1" max="1" width="12" customWidth="1"/><col min="3" max="4" width="20" customWidth="1"/></cols>
<sheetData>
<row r="1" spans="1:6"><c r="A1" s="1"><v>1</v></c><c r="B1"><f>SUM(A1:A5)</f><v>15</v></c><c r="C1"><f t="shared" ref="C1:C5" si="0">A1*2</f><v>2</v></c><c r="D1" t="s"><v>0</v></c></row>
<row r="2"><c r="A2"><v>2</v></c><c r="C2"><f t="shared" si="0"/><v>4</v></c></row>
<row r="3"><c r="A3"><v>3</v></c><c r="C3"><f t="shared" si="0"/><v>6</v></c><c r="E3" s="2"><v>45356</v></c></row>
<row r="4"><c r="A4"><v>4</v></c><c r="C4"><f t="shared" si="0"/><v>8</v></c></row>
<row r="5"><c r="A5"><v>5</v></c><c r="C5"><f t="shared" si="0"/><v>10</v></c><c r="F5"><f>$A$1+'Diğer Sayfa'!A1+"A1"</f><v>2</v></c></row>
</sheetData>
<mergeCells count="1"><mergeCell ref="D1:E2"/></mergeCells>
<conditionalFormatting sqref="A1:A5"><cfRule type="expression" dxfId="0" priority="1"><formula>A1&gt;3</formula></cfRule></conditionalFormatting>
<dataValidations count="1"><dataValidation type="whole" allowBlank="1" sqref="F1:F10"><formula1>0</formula1><formula2>100</formula2></dataValidation></dataValidations>
<hyperlinks><hyperlink ref="D1" r:id="rId9"/></hyperlinks>
<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>
</worksheet>"""
    sheet1_rels = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId9" Type="{rel}/hyperlink" Target="https://example.com/" TargetMode="External"/>
</Relationships>"""
    sheet2 = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="{main}"><sheetData><row r="1"><c r="A1"><v>1</v></c></row></sheetData></worksheet>"""
    calc = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<calcChain xmlns="{main}"><c r="B1" i="1"/><c r="C1"/><c r="C2"/><c r="C3"/><c r="C4"/><c r="C5"/><c r="F5"/></calcChain>"""
    write_zip(path, {
        "[Content_Types].xml": ct,
        "_rels/.rels": ROOT_RELS,
        "xl/workbook.xml": workbook,
        "xl/_rels/workbook.xml.rels": wb_rels,
        "xl/styles.xml": styles,
        "xl/sharedStrings.xml": sst,
        "xl/worksheets/sheet1.xml": sheet1,
        "xl/worksheets/_rels/sheet1.xml.rels": sheet1_rels,
        "xl/worksheets/sheet2.xml": sheet2,
        "xl/calcChain.xml": calc,
    })


def write_bad_files(directory):
    with open(os.path.join(directory, "empty.xlsx"), "wb"):
        pass
    with open(os.path.join(directory, "not_xlsx.xlsx"), "w", encoding="utf-8") as f:
        f.write("Bu bir Excel dosyası değil.\n")
    with open(os.path.join(directory, "legacy.xls"), "wb") as f:
        f.write(bytes([0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1]) + b"\x00" * 504)
    good = os.path.join(directory, "simple.xlsx")
    with open(good, "rb") as f:
        data = f.read()
    with open(os.path.join(directory, "corrupt.xlsx"), "wb") as f:
        f.write(data[: len(data) // 3])
    # ZIP ama XLSX değil
    write_zip(os.path.join(directory, "zip_not_xlsx.xlsx"), {"readme.txt": "merhaba"})


# ----------------------------------------------------------------------------- PDF

def register_font():
    for candidate in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if os.path.exists(candidate):
            pdfmetrics.registerFont(TTFont("TRFont", candidate))
            return "TRFont"
    return "Helvetica"


def single_page_pdf(path, font):
    c = canvas.Canvas(path, pagesize=A4)
    c.setFont(font, 22)
    c.drawString(60, 760, "Ayaz Viewer – tek sayfa")
    c.setFont(font, 14)
    c.drawString(60, 720, TURKISH)
    c.drawString(60, 690, "Aranacak kelime: anahtar")
    c.setFillColor(HexColor("#4F7CFF"))
    c.rect(60, 400, 200, 200, fill=1, stroke=0)
    c.showPage()
    c.save()


def multi_page_pdf(path, font, pages=30):
    c = canvas.Canvas(path, pagesize=A4)
    for i in range(1, pages + 1):
        c.setFont(font, 40)
        c.drawString(60, 740, f"Sayfa {i}")
        c.setFont(font, 12)
        for line in range(30):
            c.drawString(60, 680 - line * 18, f"Satır {line + 1} – örnek metin ğüşıöç")
        if i in (7, 23):
            c.setFont(font, 16)
            c.drawString(60, 100, "Burada anahtar kelime var")
        c.showPage()
    c.save()


def large_pdf(path, font, pages=400):
    c = canvas.Canvas(path, pagesize=A4)
    w, h = A4
    for i in range(1, pages + 1):
        c.setFont(font, 28)
        c.drawString(50, h - 70, f"Büyük belge – sayfa {i}/{pages}")
        c.setStrokeColor(HexColor("#8B5CF6"))
        for k in range(0, 60):
            c.line(40, 80 + k * 11, w - 40, 80 + ((k * 37 + i) % 60) * 11)
        c.showPage()
    c.save()


def mixed_sizes_pdf(path, font):
    c = canvas.Canvas(path)
    for size, label in ((A4, "A4 dikey"), (landscape(A4), "A4 yatay"), (A5, "A5")):
        c.setPageSize(size)
        c.setFont(font, 24)
        c.drawString(40, size[1] - 60, label)
        c.rect(20, 20, size[0] - 40, size[1] - 40)
        c.showPage()
    c.save()


def encrypted_pdf(path, font):
    c = canvas.Canvas(path, pagesize=A4, encrypt="parola123")
    c.setFont(font, 20)
    c.drawString(60, 760, "Şifreli belge")
    c.showPage()
    c.save()


def bad_pdfs(directory):
    with open(os.path.join(directory, "empty.pdf"), "wb"):
        pass
    with open(os.path.join(directory, "corrupt.pdf"), "wb") as f:
        f.write(b"%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer garbage\n")


def main():
    ensure_dirs()
    font = register_font()

    simple_xlsx(os.path.join(JVM_RES, "simple.xlsx"))
    multi_sheet_xlsx(os.path.join(JVM_RES, "multi_sheet.xlsx"))
    handcrafted_xlsx(os.path.join(JVM_RES, "handcrafted.xlsx"))
    formulas_xlsx(os.path.join(JVM_RES, "formulas.xlsx"))
    write_bad_files(JVM_RES)

    for name in ("simple.xlsx", "multi_sheet.xlsx", "handcrafted.xlsx", "formulas.xlsx", "corrupt.xlsx", "empty.xlsx",
                 "legacy.xls", "not_xlsx.xlsx"):
        shutil.copy(os.path.join(JVM_RES, name), os.path.join(ANDROID_ASSETS, name))

    single_page_pdf(os.path.join(ANDROID_ASSETS, "single_page.pdf"), font)
    multi_page_pdf(os.path.join(ANDROID_ASSETS, "multi_page.pdf"), font)
    large_pdf(os.path.join(ANDROID_ASSETS, "large.pdf"), font)
    mixed_sizes_pdf(os.path.join(ANDROID_ASSETS, "mixed_sizes.pdf"), font)
    encrypted_pdf(os.path.join(ANDROID_ASSETS, "encrypted.pdf"), font)
    bad_pdfs(ANDROID_ASSETS)
    # Not: Türkçe adlı kopya assets'e konmaz; cihaz testi bu kopyayı çalışma anında oluşturur.
    for name in ("single_page.pdf", "multi_page.pdf", "large.pdf", "mixed_sizes.pdf"):
        shutil.copy(os.path.join(ANDROID_ASSETS, name), os.path.join(SAMPLES, name))
    shutil.copy(os.path.join(ANDROID_ASSETS, "single_page.pdf"),
                os.path.join(SAMPLES, "Türkçe_Dosya_ğüşiöç.pdf"))
    for name in ("simple.xlsx", "multi_sheet.xlsx", "handcrafted.xlsx"):
        shutil.copy(os.path.join(JVM_RES, name), os.path.join(SAMPLES, name))
    shutil.copy(os.path.join(JVM_RES, "simple.xlsx"), os.path.join(SAMPLES, "Türkçe_Tablo_ğüşiöç.xlsx"))
    print("Örnek dosyalar üretildi. Yazı tipi:", font)


if __name__ == "__main__":
    main()
