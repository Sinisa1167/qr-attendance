$email = "sinisa.sarkanovic@student.etf.unibl.org"

$tokenResponse = Invoke-RestMethod `
  -Uri "http://192.168.1.12:8080/realms/qr-attendance/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=qr-attendance-app&username=$email&password=student123"

$STUDENT_TOKEN = $tokenResponse.access_token
Write-Host "Student token dobijen! Istice za $($tokenResponse.expires_in) sekundi."

$QR = "4f505903-fb0e-4358-869e-971132c488ac.HWalsNqIp04LHr9qDA1DsE_SZwLrpEbBrFRQxM3ggvQ"

$URL = "http://192.168.1.12:8081/api/student/attendance/checkin"

for ($i = 1; $i -le 5; $i++) {
    Write-Host "`n--- Pokusaj broj $i ---"
    $body = "{`"token`":`"$QR`"}"
    try {
        $response = Invoke-RestMethod `
            -Uri $URL `
            -Method POST `
            -Headers @{
                "Authorization" = "Bearer $STUDENT_TOKEN"
                "Content-Type" = "application/json"
            } `
            -Body $body
        Write-Host "USPJEH: Prisustvo evidentirano u $($response.checkInTime)"
    }  catch {
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
                Write-Host "GRESKA ($statusCode): $($err.error)"
            } catch {
                Write-Host "GRESKA ($statusCode): $errBody"
            }
        } else {
            Write-Host "GRESKA ($statusCode): (nema tijela odgovora)"
        }
    }
    Start-Sleep -Milliseconds 500
}