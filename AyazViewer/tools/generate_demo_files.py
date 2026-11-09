#!/usr/bin/env python3
"""Mağaza ekran görüntüleri ve tanıtım için gerçekçi örnek dosyalar üretir.

Çıktılar:
  smoketest/src/main/assets/Proje Raporu.pdf   6 sayfalık örnek proje raporu
  smoketest/src/main/assets/Bütçe 2026.xlsx    biçimli aile bütçesi (2 sayfa)
  samples/ altına aynı dosyaların kopyaları (telefonda elle denemek için)

Kullanım:  python3 tools/generate_demo_files.py
Gereken:   openpyxl, reportlab, DejaVu Sans yazı tipi
"""
import datetime
import os
import shutil

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "smoketest", "src", "main", "assets")
SAMPLES = os.path.join(ROOT, "samples")

BLUE = "2F5BFF"
PURPLE = "7C4DFF"
LIGHT = "EEF2FF"


# ----------------------------------------------------------------------------- Excel

def budget_xlsx(path):
    wb = Workbook()
    ws = wb.active
    ws.title = "Aylık Bütçe"
    months = ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran"]
    rows = [
        ("Kira", [18000] * 6),
        ("Market", [9450, 8820.5, 9910, 9300, 10150, 9780]),
        ("Faturalar", [3240, 3610, 2980, 2450, 2100, 2270]),
        ("Ulaşım", [2600, 2480, 2750, 2900, 3050, 2800]),
        ("Eğitim", [4500, 4500, 4500, 4500, 4500, 1500]),
        ("Sağlık", [750, 1200, 0, 640, 300, 980]),
        ("Giyim", [1800, 0, 2350, 1200, 0, 2600]),
        ("Eğlence", [1500, 1250, 1900, 1100, 2200, 3100]),
        ("Tasarruf", [5000, 5000, 6000, 6000, 6500, 7000]),
    ]
    thin = Side(style="thin", color="D5DAE8")
    money = '#,##0.00 "₺"'

    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=len(months) + 2)
    ws["A1"] = "2026 Aile Bütçesi – Giderler"
    ws["A1"].font = Font(bold=True, size=14, color="FFFFFF")
    ws["A1"].fill = PatternFill("solid", fgColor=PURPLE)
    ws["A1"].alignment = Alignment(horizontal="center", vertical="center")
    ws.row_dimensions[1].height = 26

    headers = ["Kategori"] + months + ["Toplam"]
    for c, h in enumerate(headers, start=1):
        cell = ws.cell(row=2, column=c, value=h)
        cell.font = Font(bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor=BLUE)
        cell.alignment = Alignment(horizontal="center")
        cell.border = Border(bottom=thin)

    for r, (name, values) in enumerate(rows, start=3):
        ws.cell(row=r, column=1, value=name).font = Font(bold=True)
        for c, v in enumerate(values, start=2):
            cell = ws.cell(row=r, column=c, value=v)
            cell.number_format = money
        total = ws.cell(row=r, column=len(months) + 2, value=sum(values))
        total.number_format = money
        total.font = Font(bold=True)
        if r % 2 == 0:
            for c in range(1, len(months) + 3):
                ws.cell(row=r, column=c).fill = PatternFill("solid", fgColor=LIGHT)

    tr = 3 + len(rows)
    ws.cell(row=tr, column=1, value="TOPLAM").font = Font(bold=True, color=BLUE)
    for c in range(2, len(months) + 3):
        s = sum(row[1][c - 2] for row in rows) if c <= len(months) + 1 else sum(sum(v) for _, v in rows)
        cell = ws.cell(row=tr, column=c, value=s)
        cell.number_format = money
        cell.font = Font(bold=True, color=BLUE)
        cell.border = Border(top=Side(style="medium", color=BLUE))
    ws.cell(row=tr, column=1).border = Border(top=Side(style="medium", color=BLUE))

    ws.column_dimensions["A"].width = 14
    for col in "BCDEFGH":
        ws.column_dimensions[col].width = 13
    ws.freeze_panes = "B3"

    inc = wb.create_sheet("Gelirler")
    inc.append(["Tarih", "Açıklama", "Tutar", "Pay"])
    for c in range(1, 5):
        cell = inc.cell(row=1, column=c)
        cell.font = Font(bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor=BLUE)
    items = [
        (datetime.date(2026, 1, 5), "Maaş – Ayşe", 52000),
        (datetime.date(2026, 1, 5), "Maaş – Çağrı", 48000),
        (datetime.date(2026, 1, 18), "Serbest çalışma", 7500),
        (datetime.date(2026, 2, 5), "Maaş – Ayşe", 52000),
        (datetime.date(2026, 2, 5), "Maaş – Çağrı", 48000),
        (datetime.date(2026, 2, 21), "Kira geliri (dükkân)", 12000),
    ]
    total = sum(i[2] for i in items)
    for d, text, amount in items:
        inc.append([d, text, amount, amount / total])
    for r in range(2, len(items) + 2):
        inc.cell(row=r, column=1).number_format = "dd.mm.yyyy"
        inc.cell(row=r, column=3).number_format = money
        inc.cell(row=r, column=4).number_format = "0.0%"
    inc.column_dimensions["A"].width = 12
    inc.column_dimensions["B"].width = 24
    inc.column_dimensions["C"].width = 14
    inc.freeze_panes = "A2"
    wb.save(path)


# ----------------------------------------------------------------------------- PDF

def register_font():
    regular = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    pdfmetrics.registerFont(TTFont("TR", regular))
    pdfmetrics.registerFont(TTFont("TRB", bold if os.path.exists(bold) else regular))


PARAGRAPHS = [
    "Bu rapor, şehir kütüphanesinin dijital arşiv projesinin ilk çeyrek sonuçlarını özetler. Projenin amacı, "
    "1950'den bu yana biriken gazete, dergi ve el yazması koleksiyonunu taranmış ve aranabilir hale getirmektir.",
    "İlk üç ayda toplam 48.600 sayfa tarandı ve metin tanıma işleminden geçirildi. Tanıma doğruluğu Türkçe "
    "karakterler (ğ, ü, ş, ı, ö, ç) için yapılan iyileştirmelerle yüzde 97,4'e ulaştı.",
    "Gönüllü ekip 12 kişiden 19 kişiye çıktı. Haftalık kalite kontrol toplantıları düzenli olarak yapıldı ve "
    "hatalı sayfaların oranı yüzde 4,1'den yüzde 1,8'e düştü.",
    "Bir sonraki dönemde el yazmalarının özel tarayıcıyla işlenmesi, okuyucu arayüzünün tamamlanması ve "
    "okullarla ortak tanıtım etkinliklerinin düzenlenmesi planlanmaktadır.",
]


def wrap(c, text, font, size, width):
    words, lines, line = text.split(), [], ""
    for w in words:
        cand = (line + " " + w).strip()
        if c.stringWidth(cand, font, size) <= width:
            line = cand
        else:
            lines.append(line)
            line = w
    if line:
        lines.append(line)
    return lines


def report_pdf(path):
    register_font()
    c = canvas.Canvas(path, pagesize=A4)
    W, H = A4
    m = 56
    sections = [
        ("1. Özet", PARAGRAPHS[0]),
        ("2. İlerleme", PARAGRAPHS[1]),
        ("3. Ekip ve kalite", PARAGRAPHS[2]),
        ("4. Bütçe", None),
        ("5. Sonraki adımlar", PARAGRAPHS[3]),
        ("6. Ekler", PARAGRAPHS[0]),
    ]
    for i, (title, body) in enumerate(sections, start=1):
        # üst bant
        c.setFillColor(HexColor("#" + BLUE))
        c.rect(0, H - 70, W, 70, fill=1, stroke=0)
        c.setFillColor(HexColor("#FFFFFF"))
        c.setFont("TRB", 16)
        c.drawString(m, H - 44, "Dijital Arşiv Projesi – 1. Çeyrek Raporu")
        c.setFont("TR", 9)
        c.drawRightString(W - m, H - 44, "Mart 2026")
        y = H - 120
        if i == 1:
            c.setFillColor(HexColor("#1B1B1F"))
            c.setFont("TRB", 26)
            c.drawString(m, y, "Proje Raporu")
            y -= 30
            c.setFont("TR", 12)
            c.setFillColor(HexColor("#5F6370"))
            c.drawString(m, y, "Hazırlayan: Proje Koordinasyon Ekibi")
            y -= 40
        c.setFillColor(HexColor("#" + PURPLE))
        c.setFont("TRB", 17)
        c.drawString(m, y, title)
        y -= 26
        c.setFillColor(HexColor("#1B1B1F"))
        if body:
            for para in (body, PARAGRAPHS[(i + 1) % 4]):
                for line in wrap(c, para, "TR", 11, W - 2 * m):
                    c.setFont("TR", 11)
                    c.drawString(m, y, line)
                    y -= 16
                y -= 10
        if i in (1, 4):
            # basit tablo
            data = [("Kalem", "Planlanan", "Gerçekleşen"), ("Tarayıcı kiralama", "120.000 ₺", "112.500 ₺"),
                    ("Depolama", "45.000 ₺", "41.200 ₺"), ("Eğitim", "18.000 ₺", "19.600 ₺"), ("Toplam", "183.000 ₺", "173.300 ₺")]
            y -= 6
            colw = [(W - 2 * m) * f for f in (0.46, 0.27, 0.27)]
            for r, row in enumerate(data):
                if r == 0:
                    c.setFillColor(HexColor("#" + BLUE))
                    c.rect(m, y - 6, W - 2 * m, 22, fill=1, stroke=0)
                    c.setFillColor(HexColor("#FFFFFF"))
                    c.setFont("TRB", 10)
                else:
                    if r % 2 == 0:
                        c.setFillColor(HexColor("#" + LIGHT))
                        c.rect(m, y - 6, W - 2 * m, 22, fill=1, stroke=0)
                    c.setFillColor(HexColor("#1B1B1F"))
                    c.setFont("TRB" if r == len(data) - 1 else "TR", 10)
                x = m + 8
                for k, val in enumerate(row):
                    c.drawString(x, y, val)
                    x += colw[k]
                y -= 22
            y -= 20
        if i in (2, 4):
            # çubuk grafik
            c.setFont("TRB", 11)
            c.setFillColor(HexColor("#1B1B1F"))
            c.drawString(m, y, "Aylara göre taranan sayfa (bin)")
            y -= 16
            vals = [("Ocak", 13.2), ("Şubat", 16.1), ("Mart", 19.3)]
            base = y - 150
            for k, (label, v) in enumerate(vals):
                x = m + 30 + k * 130
                h = v * 7
                c.setFillColor(HexColor(["#2F5BFF", "#5B4DF2", "#9B5CF6"][k]))
                c.roundRect(x, base, 70, h, 6, fill=1, stroke=0)
                c.setFillColor(HexColor("#1B1B1F"))
                c.setFont("TR", 10)
                c.drawCentredString(x + 35, base - 14, label)
                c.drawCentredString(x + 35, base + h + 6, f"{v:.1f}".replace(".", ","))
            y = base - 40
        c.setFont("TR", 9)
        c.setFillColor(HexColor("#8A8F9C"))
        c.drawCentredString(W / 2, 30, f"Sayfa {i} / {len(sections)}")
        c.showPage()
    c.save()


def main():
    os.makedirs(ASSETS, exist_ok=True)
    pdf = os.path.join(ASSETS, "Proje Raporu.pdf")
    xlsx = os.path.join(ASSETS, "Bütçe 2026.xlsx")
    report_pdf(pdf)
    budget_xlsx(xlsx)
    for f in (pdf, xlsx):
        shutil.copy(f, os.path.join(SAMPLES, os.path.basename(f)))
    print("Kaydedildi:", pdf, xlsx)


if __name__ == "__main__":
    main()
