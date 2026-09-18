$KEYCLOAK = "http://192.168.1.12:8080/realms/qr-attendance/protocol/openid-connect/token"
$URL = "http://192.168.1.12:8081/api/student/attendance/checkin"
$STUDENT = "nevena.jovanovic@student.etf.unibl.org"
$QR = "b8d65836-251e-43c3-addf-8d46115731b8.J6D0lOR_QhMHNsnnyv-iQQarmGx3BvmsZSdSMJbEG0c"

function Pokusaj-Prijavu {
    $tok = (Invoke-RestMethod -Uri $KEYCLOAK -Method POST -ContentType "application/x-www-form-urlencoded" `
        -Body "grant_type=password&client_id=qr-attendance-app&username=$STUDENT&password=student123").access_token
    try {
        $r = Invoke-RestMethod -Uri $URL -Method POST `
            -Headers @{ Authorization = "Bearer $tok"; "Content-Type" = "application/json" } `
            -Body "{`"token`":`"$QR`"}"
        Write-Host "  USPJEH: $($r.checkInTime)"
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
        Write-Host "  GRESKA ($statusCode): $errBody"
    }
}

Write-Host "1) Gasim Redis..."
docker stop qr-redis | Out-Null
Start-Sleep -Seconds 2

Write-Host "`n2) Pokusaj prijave bez Redisa (ocekivano 503 - servis nedostupan):"
Pokusaj-Prijavu

Write-Host "`n3) Vracam Redis..."
docker start qr-redis | Out-Null
Start-Sleep -Seconds 5

Write-Host "`n4) Ponovna prijava - sistem se oporavio (ocekivano USPJEH, osim ako je token u medjuvremenu istekao):"
Pokusaj-Prijavu