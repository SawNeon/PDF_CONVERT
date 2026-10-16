$ErrorActionPreference = 'Stop'
$action = $env:PDFLOCAL_ACTION
$in = $env:PDFLOCAL_IN
$out = $env:PDFLOCAL_OUT
$list = $env:PDFLOCAL_LIST
$noPassword = 'pdflocal-sem-senha-' + [guid]::NewGuid().ToString('N')
$doNotSave = 0

function Release($comObject) {
    if ($null -ne $comObject) {
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($comObject)
    }
}

function Convert-Word([string]$source, [string]$target) {
    $app = New-Object -ComObject Word.Application
    try {
        $app.Visible = $false
        $app.DisplayAlerts = 0
        $app.AutomationSecurity = 3
        $document = $app.Documents.Open($source, $false, $true, $false, $noPassword)
        try {
            $document.ExportAsFixedFormat($target, 17)
        } finally {
            $document.Close([ref]$doNotSave)
            Release $document
        }
    } finally {
        if ($app.Documents.Count -eq 0) {
            $app.Quit([ref]$doNotSave)
        }
        Release $app
    }
}

function Convert-Excel([string]$source, [string]$target) {
    $app = New-Object -ComObject Excel.Application
    try {
        $app.Visible = $false
        $app.DisplayAlerts = $false
        $app.AutomationSecurity = 3
        $app.AskToUpdateLinks = $false
        $workbook = $app.Workbooks.Open($source, 0, $true, [System.Type]::Missing, $noPassword)
        try {
            $workbook.ExportAsFixedFormat(0, $target)
        } finally {
            $workbook.Close($false)
            Release $workbook
        }
    } finally {
        if ($app.Workbooks.Count -eq 0) {
            $app.Quit()
        }
        Release $app
    }
}

function Convert-PowerPoint([string]$source, [string]$target) {
    $app = New-Object -ComObject PowerPoint.Application
    try {
        $presentation = $app.Presentations.Open($source, -1, 0, 0)
        try {
            $presentation.SaveAs($target, 32)
        } finally {
            $presentation.Close()
            Release $presentation
        }
    } finally {
        if ($app.Presentations.Count -eq 0) {
            $app.Quit()
        }
        Release $app
    }
}

function Build-Slides([string]$listFile, [string]$target) {
    Add-Type -AssemblyName System.Drawing
    $images = @(Get-Content -LiteralPath $listFile -Encoding UTF8 | Where-Object { $_ -ne '' })
    $app = New-Object -ComObject PowerPoint.Application
    try {
        $presentation = $app.Presentations.Add(0)
        try {
            $first = [System.Drawing.Image]::FromFile($images[0])
            $ratio = $first.Width / $first.Height
            $first.Dispose()
            $slideHeight = 540
            $slideWidth = [math]::Round($slideHeight * $ratio)
            $presentation.PageSetup.SlideHeight = $slideHeight
            $presentation.PageSetup.SlideWidth = $slideWidth
            foreach ($path in $images) {
                $bitmap = [System.Drawing.Image]::FromFile($path)
                $pixelWidth = $bitmap.Width
                $pixelHeight = $bitmap.Height
                $bitmap.Dispose()
                $scale = [math]::Min($slideWidth / $pixelWidth, $slideHeight / $pixelHeight)
                $width = $pixelWidth * $scale
                $height = $pixelHeight * $scale
                $slide = $presentation.Slides.Add($presentation.Slides.Count + 1, 12)
                [void]$slide.Shapes.AddPicture($path, 0, -1, ($slideWidth - $width) / 2, ($slideHeight - $height) / 2, $width, $height)
            }
            $presentation.SaveAs($target, 24)
        } finally {
            $presentation.Close()
            Release $presentation
        }
    } finally {
        if ($app.Presentations.Count -eq 0) {
            $app.Quit()
        }
        Release $app
    }
}

try {
    switch ($action) {
        'WordToPdf' { Convert-Word $in $out }
        'ExcelToPdf' { Convert-Excel $in $out }
        'PowerPointToPdf' { Convert-PowerPoint $in $out }
        'ImagesToPowerPoint' { Build-Slides $list $out }
        default { exit 64 }
    }
    exit 0
} catch {
    exit 2
}
