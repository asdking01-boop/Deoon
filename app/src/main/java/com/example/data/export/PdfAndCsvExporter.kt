package com.example.data.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.BuildConfig
import com.example.R
import com.example.core.money.CurrencyCode
import com.example.core.money.MoneyUtils
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtLineItemEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.preferences.MerchantSettings
import com.example.domain.model.CustomerFinancialSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PdfAndCsvExporter {

    private const val PAGE_WIDTH = 595 // A4 @ 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    private fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun cairoTypeface(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT

    private fun dateStr(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

    private fun shortDateStr(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        paint: TextPaint,
        isRtl: Boolean,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        val dir = if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(20))
            .setAlignment(alignment)
            .setTextDirection(dir)
            .setLineSpacing(2f, 1.1f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    private fun drawHeader(
        context: Context,
        canvas: Canvas,
        merchant: MerchantSettings,
        docTitle: String,
        docNumber: String,
        isArabic: Boolean
    ): Float {
        val navyPaint = Paint().apply { color = Color.parseColor("#0A1628") }
        val goldPaint = Paint().apply {
            color = Color.parseColor("#D4AF37")
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(RectF(MARGIN, MARGIN, PAGE_WIDTH - MARGIN, MARGIN + 92f), 12f, 12f, navyPaint)

        val tf = cairoTypeface(context)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D4AF37")
            textSize = 16f
            typeface = Typeface.create(tf, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f
            typeface = tf
        }

        // Optional merchant logo
        var textWidth = PAGE_WIDTH - MARGIN * 2 - 24f
        merchant.merchantLogoUri?.let { uriStr ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val logoRect = if (isArabic) {
                            RectF(MARGIN + 12f, MARGIN + 16f, MARGIN + 68f, MARGIN + 72f)
                        } else {
                            RectF(PAGE_WIDTH - MARGIN - 68f, MARGIN + 16f, PAGE_WIDTH - MARGIN - 12f, MARGIN + 72f)
                        }
                        canvas.drawBitmap(bmp, null, logoRect, null)
                        textWidth -= 68f
                    }
                }
            }
        }

        val textX = if (isArabic && merchant.merchantLogoUri != null) MARGIN + 80f else MARGIN + 14f
        var cursorY = MARGIN + 14f
        cursorY = drawTextBlock(canvas, merchant.merchantName, textX, cursorY, textWidth, titlePaint, isArabic)
        if (merchant.merchantPhone.isNotBlank()) {
            cursorY = drawTextBlock(canvas, merchant.merchantPhone, textX, cursorY + 3f, textWidth, subPaint, isArabic)
        }
        drawTextBlock(canvas, "$docTitle  •  $docNumber", textX, cursorY + 5f, textWidth, subPaint, isArabic)

        val bottomY = MARGIN + 104f
        canvas.drawLine(MARGIN, bottomY, PAGE_WIDTH - MARGIN, bottomY, goldPaint)
        return bottomY + 14f
    }

    suspend fun generateInvoicePdf(
        context: Context,
        merchant: MerchantSettings,
        customer: CustomerEntity,
        debt: DebtEntity,
        items: List<DebtLineItemEntity>,
        isArabic: Boolean
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        val tf = cairoTypeface(context)

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1628")
            textSize = 11.5f
            typeface = tf
        }
        val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1628")
            textSize = 12f
            typeface = Typeface.create(tf, Typeface.BOLD)
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 10f
            typeface = tf
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        val width = PAGE_WIDTH - MARGIN * 2
        var y = drawHeader(
            context, canvas, merchant,
            if (isArabic) "فاتورة دين" else "Debt Invoice",
            debt.invoiceNumber, isArabic
        )

        val custInfo = if (isArabic) {
            "العميل: ${customer.fullName}\nالهاتف: ${customer.phone}\nتاريخ الفاتورة: ${dateStr(debt.createdAt)}" +
                (debt.dueDate?.let { "\nتاريخ الاستحقاق: ${shortDateStr(it)}" } ?: "")
        } else {
            "Customer: ${customer.fullName}\nPhone: ${customer.phone}\nInvoice Date: ${dateStr(debt.createdAt)}" +
                (debt.dueDate?.let { "\nDue Date: ${shortDateStr(it)}" } ?: "")
        }
        y = drawTextBlock(canvas, custInfo, MARGIN, y, width, bodyPaint, isArabic) + 12f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 10f

        if (items.isNotEmpty()) {
            val headerTxt = if (isArabic) "البنود والمنتجات:" else "Items & Services:"
            y = drawTextBlock(canvas, headerTxt, MARGIN, y, width, boldPaint, isArabic) + 6f
            for ((idx, item) in items.withIndex()) {
                if (y > PAGE_HEIGHT - 130f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = MARGIN
                }
                val lineText = if (isArabic) {
                    "${idx + 1}. ${item.productName} — الكمية: ${item.quantity} × ${MoneyUtils.format(item.unitPriceMinor, debt.currency, true)} = ${MoneyUtils.format(item.lineTotalMinor, debt.currency, true)}"
                } else {
                    "${idx + 1}. ${item.productName} — Qty: ${item.quantity} × ${MoneyUtils.format(item.unitPriceMinor, debt.currency, false)} = ${MoneyUtils.format(item.lineTotalMinor, debt.currency, false)}"
                }
                y = drawTextBlock(canvas, lineText, MARGIN, y, width, bodyPaint, isArabic) + 6f
            }
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 10f
        }

        val summaryLines = if (isArabic) {
            buildString {
                append("العملة: ${debt.currency.label(true)}\n")
                append("المجموع الفرعي: ${MoneyUtils.format(debt.subtotalMinor, debt.currency, true)}\n")
                if (debt.discountMinor > 0L) append("الخصم: -${MoneyUtils.format(debt.discountMinor, debt.currency, true)}\n")
                if (debt.taxMinor > 0L) append("الضريبة: +${MoneyUtils.format(debt.taxMinor, debt.currency, true)}\n")
                append("الإجمالي: ${MoneyUtils.format(debt.totalAmountMinor, debt.currency, true)}\n")
                append("المدفوع: ${MoneyUtils.format(debt.paidAmountMinor, debt.currency, true)}\n")
                append("الرصيد المتبقي: ${MoneyUtils.format(debt.remainingAmountMinor, debt.currency, true)}")
            }
        } else {
            buildString {
                append("Currency: ${debt.currency.label(false)}\n")
                append("Subtotal: ${MoneyUtils.format(debt.subtotalMinor, debt.currency, false)}\n")
                if (debt.discountMinor > 0L) append("Discount: -${MoneyUtils.format(debt.discountMinor, debt.currency, false)}\n")
                if (debt.taxMinor > 0L) append("Tax: +${MoneyUtils.format(debt.taxMinor, debt.currency, false)}\n")
                append("Total Amount: ${MoneyUtils.format(debt.totalAmountMinor, debt.currency, false)}\n")
                append("Paid Amount: ${MoneyUtils.format(debt.paidAmountMinor, debt.currency, false)}\n")
                append("Remaining Balance: ${MoneyUtils.format(debt.remainingAmountMinor, debt.currency, false)}")
            }
        }
        y = drawTextBlock(canvas, summaryLines, MARGIN, y, width, boldPaint, isArabic) + 12f

        if (debt.notes.isNotBlank()) {
            val notesTitle = if (isArabic) "ملاحظات: ${debt.notes}" else "Notes: ${debt.notes}"
            y = drawTextBlock(canvas, notesTitle, MARGIN, y, width, mutedPaint, isArabic) + 10f
        }

        drawTextBlock(
            canvas,
            if (isArabic) "تم الإنشاء بواسطة دفتر التاجر" else "Generated by Smart Debt Book",
            MARGIN, PAGE_HEIGHT - MARGIN - 16f, width, mutedPaint, isArabic
        )

        doc.finishPage(page)
        val outFile = File(exportsDir(context), "invoice_${debt.invoiceNumber}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    suspend fun generatePaymentReceiptPdf(
        context: Context,
        merchant: MerchantSettings,
        customer: CustomerEntity,
        payment: PaymentEntity,
        isArabic: Boolean
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        val tf = cairoTypeface(context)
        val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1628")
            textSize = 12f
            typeface = Typeface.create(tf, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1628")
            textSize = 11.5f
            typeface = tf
        }
        val width = PAGE_WIDTH - MARGIN * 2

        var y = drawHeader(
            context, canvas, merchant,
            if (isArabic) "إيصال استلام دفعة" else "Payment Receipt",
            payment.receiptNumber, isArabic
        )

        val details = if (isArabic) {
            buildString {
                append("العميل: ${customer.fullName} (${customer.phone})\n")
                append("تاريخ الدفع: ${dateStr(payment.paymentDate)}\n")
                append("طريقة الدفع: ${payment.paymentMethod.name}\n")
                append("الحالة: ${payment.status.name}\n\n")
                append("المبلغ المدفوع: ${MoneyUtils.format(payment.paymentAmountMinor, payment.paymentCurrency, true)}\n")
                append("عملة الحساب المستهدفة: ${payment.targetCurrency.label(true)}\n")
                append("سعر الصرف الموثق: 1 USD = ${payment.exchangeRateUsed} ILS\n")
                append("القيمة المحوّلة المعتمدة: ${MoneyUtils.format(payment.convertedAmountMinor, payment.targetCurrency, true)}\n")
                if (payment.overpaymentCreditMinor > 0L) {
                    append("رصيد دائن للعميل: ${MoneyUtils.format(payment.overpaymentCreditMinor, payment.targetCurrency, true)}\n")
                }
                if (payment.notes.isNotBlank()) {
                    append("\nملاحظات: ${payment.notes}")
                }
            }
        } else {
            buildString {
                append("Customer: ${customer.fullName} (${customer.phone})\n")
                append("Payment Date: ${dateStr(payment.paymentDate)}\n")
                append("Method: ${payment.paymentMethod.name}\n")
                append("Status: ${payment.status.name}\n\n")
                append("Paid Amount: ${MoneyUtils.format(payment.paymentAmountMinor, payment.paymentCurrency, false)}\n")
                append("Account Currency: ${payment.targetCurrency.label(false)}\n")
                append("Exchange Rate Used: 1 USD = ${payment.exchangeRateUsed} ILS\n")
                append("Converted Amount: ${MoneyUtils.format(payment.convertedAmountMinor, payment.targetCurrency, false)}\n")
                if (payment.overpaymentCreditMinor > 0L) {
                    append("Customer Credit: ${MoneyUtils.format(payment.overpaymentCreditMinor, payment.targetCurrency, false)}\n")
                }
                if (payment.notes.isNotBlank()) {
                    append("\nNotes: ${payment.notes}")
                }
            }
        }

        drawTextBlock(canvas, details, MARGIN, y, width, bodyPaint, isArabic)
        drawTextBlock(
            canvas,
            if (isArabic) "شكراً لتعاملكم معنا" else "Thank you for your business",
            MARGIN, PAGE_HEIGHT - MARGIN - 30f, width, boldPaint, isArabic
        )

        doc.finishPage(page)
        val outFile = File(exportsDir(context), "receipt_${payment.receiptNumber}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    suspend fun generateCustomerStatementPdf(
        context: Context,
        merchant: MerchantSettings,
        summary: CustomerFinancialSummary,
        debts: List<DebtEntity>,
        payments: List<PaymentEntity>,
        isArabic: Boolean
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        val tf = cairoTypeface(context)
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1628")
            textSize = 11f
            typeface = tf
        }
        val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1628")
            textSize = 12f
            typeface = Typeface.create(tf, Typeface.BOLD)
        }
        val width = PAGE_WIDTH - MARGIN * 2

        var y = drawHeader(
            context, canvas, merchant,
            if (isArabic) "كشف حساب عميل" else "Customer Statement",
            summary.customer.fullName, isArabic
        )

        val balancesText = if (isArabic) {
            "الهاتف: ${summary.customer.phone}\n" +
                "الرصيد المستحق (شيكل): ${MoneyUtils.format(summary.outstandingIlsMinor, CurrencyCode.ILS, true)}\n" +
                "الرصيد المستحق (دولار): ${MoneyUtils.format(summary.outstandingUsdMinor, CurrencyCode.USD, true)}\n" +
                "إجمالي المدفوع (شيكل): ${MoneyUtils.format(summary.paidIlsMinor, CurrencyCode.ILS, true)}\n" +
                "إجمالي المدفوع (دولار): ${MoneyUtils.format(summary.paidUsdMinor, CurrencyCode.USD, true)}"
        } else {
            "Phone: ${summary.customer.phone}\n" +
                "Outstanding (ILS): ${MoneyUtils.format(summary.outstandingIlsMinor, CurrencyCode.ILS, false)}\n" +
                "Outstanding (USD): ${MoneyUtils.format(summary.outstandingUsdMinor, CurrencyCode.USD, false)}\n" +
                "Total Paid (ILS): ${MoneyUtils.format(summary.paidIlsMinor, CurrencyCode.ILS, false)}\n" +
                "Total Paid (USD): ${MoneyUtils.format(summary.paidUsdMinor, CurrencyCode.USD, false)}"
        }
        y = drawTextBlock(canvas, balancesText, MARGIN, y, width, boldPaint, isArabic) + 12f

        y = drawTextBlock(canvas, if (isArabic) "سجل الديون:" else "Debts Ledger:", MARGIN, y, width, boldPaint, isArabic) + 4f
        for (d in debts) {
            if (y > PAGE_HEIGHT - 80f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }
            val line = "${shortDateStr(d.createdAt)} | ${d.invoiceNumber} | ${MoneyUtils.format(d.totalAmountMinor, d.currency, isArabic)} | ${if (isArabic) "متبقي" else "Rem"}: ${MoneyUtils.format(d.remainingAmountMinor, d.currency, isArabic)} | ${d.status.name}"
            y = drawTextBlock(canvas, line, MARGIN, y, width, bodyPaint, isArabic) + 4f
        }

        y += 10f
        y = drawTextBlock(canvas, if (isArabic) "سجل الدفعات:" else "Payments Ledger:", MARGIN, y, width, boldPaint, isArabic) + 4f
        for (p in payments) {
            if (y > PAGE_HEIGHT - 80f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }
            val line = "${shortDateStr(p.paymentDate)} | ${p.receiptNumber} | ${MoneyUtils.format(p.paymentAmountMinor, p.paymentCurrency, isArabic)} -> ${MoneyUtils.format(p.convertedAmountMinor, p.targetCurrency, isArabic)} (${p.status.name})"
            y = drawTextBlock(canvas, line, MARGIN, y, width, bodyPaint, isArabic) + 4f
        }

        doc.finishPage(page)
        val outFile = File(exportsDir(context), "statement_${summary.customer.id.take(8)}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    suspend fun generateFinancialReportPdf(
        context: Context,
        merchant: MerchantSettings,
        title: String,
        rows: List<Pair<String, String>>,
        isArabic: Boolean
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        val tf = cairoTypeface(context)
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1628")
            textSize = 11f
            typeface = tf
        }
        val width = PAGE_WIDTH - MARGIN * 2
        var y = drawHeader(context, canvas, merchant, title, shortDateStr(System.currentTimeMillis()), isArabic)

        for ((label, value) in rows) {
            if (y > PAGE_HEIGHT - 60f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }
            y = drawTextBlock(canvas, "$label:  $value", MARGIN, y, width, bodyPaint, isArabic) + 6f
        }

        doc.finishPage(page)
        val outFile = File(exportsDir(context), "report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, null)
    }

    fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String,
        preferWhatsApp: Boolean = false
    ) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (preferWhatsApp) {
            val waIntent = Intent(baseIntent).setPackage("com.whatsapp")
            try {
                context.startActivity(waIntent)
                return
            } catch (_: ActivityNotFoundException) {
                val wabIntent = Intent(baseIntent).setPackage("com.whatsapp.w4b")
                try {
                    context.startActivity(wabIntent)
                    return
                } catch (_: ActivityNotFoundException) {
                    // Fallback to system share sheet
                }
            }
        }
        context.startActivity(Intent.createChooser(baseIntent, chooserTitle))
    }

    fun shareTextViaWhatsApp(context: Context, phone: String?, message: String) {
        val cleanDigits = phone?.filter { it.isDigit() } ?: ""
        if (cleanDigits.length >= 7) {
            val encoded = URLEncoder.encode(message, "UTF-8")
            val waUri = Uri.parse("https://wa.me/$cleanDigits?text=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, waUri)
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // fallback below
            }
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(sendIntent, "WhatsApp"))
    }

    suspend fun exportCsvToUri(
        context: Context,
        uri: Uri,
        headers: List<String>,
        rows: List<List<String>>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder()
            sb.append('\uFEFF') // UTF-8 BOM for Arabic Excel compatibility
            sb.append(headers.joinToString(",") { escapeCsv(it) }).append('\n')
            for (row in rows) {
                sb.append(row.joinToString(",") { escapeCsv(it) }).append('\n')
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Cannot write CSV")
            rows.size
        }
    }

    suspend fun importProductsFromCsvUri(
        context: Context,
        uri: Uri
    ): Result<List<ProductEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val list = mutableListOf<ProductEntity>()
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                    lines.drop(1).forEach { rawLine ->
                        val line = rawLine.trim().removePrefix("\uFEFF")
                        if (line.isNotEmpty()) {
                            val cols = parseCsvLine(line)
                            if (cols.isNotEmpty() && cols[0].isNotBlank()) {
                                val name = cols[0].trim()
                                val code = cols.getOrNull(1)?.trim() ?: ""
                                val purchaseMinor = MoneyUtils.parseToMinorUnits(cols.getOrNull(2) ?: "0", allowZero = true) ?: 0L
                                val sellingMinor = MoneyUtils.parseToMinorUnits(cols.getOrNull(3) ?: "0", allowZero = true) ?: 0L
                                val currency = CurrencyCode.fromCode(cols.getOrNull(4))
                                val qty = cols.getOrNull(5)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                                val category = cols.getOrNull(6)?.trim() ?: ""
                                list += ProductEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    codeOrBarcode = code,
                                    purchasePriceMinor = purchaseMinor,
                                    sellingPriceMinor = sellingMinor,
                                    currency = currency,
                                    availableQty = qty,
                                    category = category,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            }
                        }
                    }
                }
            } ?: error("Cannot read CSV")
            list
        }
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    cur.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString())
                cur.clear()
            } else {
                cur.append(ch)
            }
            i++
        }
        result.add(cur.toString())
        return result
    }
}
