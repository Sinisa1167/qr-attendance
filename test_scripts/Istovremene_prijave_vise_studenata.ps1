$QR = "5cc2c3d6-802b-45b2-bafd-45f2becad70e.B7dnJSYfkQzQs28VBlN3R_BQNPPmcqFdAtkflIjgZjY"
$URL = "http://192.168.1.12:8081/api/student/attendance/checkin"
$KEYCLOAK = "http://192.168.1.12:8080/realms/qr-attendance/protocol/openid-connect/token"

$studenti = @(
    "sinisa.sarkanovic@student.etf.unibl.org",
    "filip.jankovic@student.etf.unibl.org",
    "nevena.jovanovic@student.etf.unibl.org",
    "jelena.maletic@student.etf.unibl.org",
    "danilo.todorovic@student.etf.unibl.org"
)

Write-Host "Dobijeam tokene za $($studenti.Count) studenata..."
$tokeni = @()
foreach ($email in $studenti) {
    try {
        $res = Invoke-RestMethod `
            -Uri $KEYCLOAK `
            -Method POST `
            -ContentType "application/x-www-form-urlencoded" `
            -Body "grant_type=password&client_id=qr-attendance-app&username=$email&password=student123"
        $tokeni += $res.access_token
        Write-Host "Token dobijen: $email"
    } catch {
        Write-Host "GRESKA pri dobijanju tokena za: $email - $($_.Exception.Message)"
    }
}

Write-Host "`nSaljemo $($tokeni.Count) istovremenih zahtjeva...`n"

$jobovi = @()
for ($i = 0; $i -lt $tokeni.Count; $i++) {
    $tok = $tokeni[$i]
    $email = $studenti[$i]
    $jobovi += Start-Job -ScriptBlock {
        param($url, $qr, $tok, $email)
        $body = "{`"token`":`"$qr`"}"
        try {
            $response = Invoke-RestMethod `
                -Uri $url `
                -Method POST `
                -Headers @{
                    "Authorization" = "Bearer $tok"
                    "Content-Type" = "application/json"
                } `
                -Body $body
            return "USPJEH [$email]: Evidentirano u $($response.checkInTime)"
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            $errBody = $_.ErrorDetails.Message
            if (-not $errBody -and $_.Exception.Response) {
                try {
                    $stream = $_.Exception.Response.GetResponseStream()
                    $reader = New-Object System.IO.StreamReader($stream)
                    $errBody = $reader.ReadToEnd()
                } catch {}
            }
            if ($errBody) {
                try {
                    $err = $errBody | ConvertFrom-Json
                    return "GRESKA ($statusCode) [$email]: $($err.error)"
                } catch {
                    return "GRESKA ($statusCode) [$email]: $errBody"
                }
            }
            return "GRESKA ($statusCode) [$email]: (nema tijela odgovora)"
        }
    } -ArgumentList $URL, $QR, $tok, $email
}

$jobovi | Wait-Job | ForEach-Object {
    $result = Receive-Job -Job $_
    Write-Host $result
    Remove-Job -Job $_
}

Write-Host "`nGotovo! Provjeri Live Monitor u browseru."