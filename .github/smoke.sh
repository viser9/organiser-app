#!/bin/bash
# Emulator smoke test: install, grant, open every screen, screenshot, fail on crash.
set -x
PKG=com.viser.organiser
APK=app/build/outputs/apk/release/app-release.apk
mkdir -p shots
# 1) Upgrade test: install the previous release, open it (creates the v1 database), then upgrade in place
OLD=$(ls old/*.apk 2>/dev/null | head -1)
if [ -n "$OLD" ]; then
  adb install "$OLD" && adb shell am start -n $PKG/.MainActivity && sleep 8
  adb shell am force-stop $PKG
  echo "Upgrading from $OLD" > shots/upgrade.txt
fi
adb install -r "$APK" || exit 1
for p in POST_NOTIFICATIONS RECEIVE_SMS READ_SMS; do adb shell pm grant $PKG android.permission.$p; done
adb logcat -c

NOW=$(( $(date +%s) * 1000 ))
# Seed bank SMS into the inbox (works on emulator images where shell may write the SMS provider)
adb shell content insert --uri content://sms/inbox --bind address:s:VM-SBIUPI --bind "body:s:'Dear UPI user A/C X4821 debited by 349.0 on date 27Sep26 trf to SWIGGY Refno 426512345678. If not u? call 1800111109. -SBI'" --bind date:l:$NOW --bind read:i:1
adb shell content insert --uri content://sms/inbox --bind address:s:AD-HDFCBK --bind "body:s:'Spent Rs.1249.00 On HDFC Bank Card 1234 At AMAZON On 2026-09-27:16:18:00 Not You? Call 18002586161'" --bind date:l:$((NOW+1000)) --bind read:i:1
adb shell content insert --uri content://sms/inbox --bind address:s:JD-SLICEIT --bind "body:s:'Rs. 212 paid to Uber India from your slice account. UPI Ref: 426511112222'" --bind date:l:$((NOW+2000)) --bind read:i:1
adb shell content insert --uri content://sms/inbox --bind address:s:VM-SBIINB --bind "body:s:'Your OTP for transaction of Rs.500 at AMAZON is 123456. Do not share.'" --bind date:l:$((NOW+3000)) --bind read:i:1

adb shell content query --uri content://sms/inbox --projection address:body > shots/sms-inbox.txt 2>&1

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > /tmp/ui.xml
  B=$(python3 - "$1" <<'PY'
import re,sys
x=open('/tmp/ui.xml').read(); t=sys.argv[1]
for m in re.finditer(r'<node [^>]*>', x):
    n=m.group(0)
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  echo "tap '$1' at $B" >> shots/taps.txt
  [ -n "$B" ] && adb shell input tap $B
}

shot() { sleep "${2:-4}"; adb exec-out screencap -p > "shots/$1.png"; }
go() { adb shell am start -n $PKG/.MainActivity -f 0x24000000 --es route "$1" ${2:+--es id "$2"}; }

adb shell am start -n $PKG/.MainActivity; shot 01-home 10
# Deliver real incoming SMS through the modem (tests SmsReceiver -> parser -> notification)
adb emu sms send VMSBIUPI "Dear UPI user A/C X4821 debited by 349.0 on date 27Sep26 trf to SWIGGY Refno 426512345678. If not u? call 1800111109. -SBI" | tee shots/emu-sms.txt
sleep 2
adb emu sms send ADHDFCBK "Spent Rs.1249.00 On HDFC Bank Card 1234 At AMAZON On 2026-09-27:16:18:00 Not You? Call 18002586161" | tee -a shots/emu-sms.txt
sleep 6
adb shell cmd statusbar expand-notifications; shot 01b-notifications 3
adb shell cmd statusbar collapse; sleep 1
go home; shot 01c-home-after-sms 3
go review; shot 02-review
tap_text "Yes"; sleep 1
tap_text "Add a person's name"; sleep 1; adb shell input text "Aaquib"; adb shell input keyevent KEYCODE_ENTER; sleep 1
tap_text "Add a person's name"; sleep 1; adb shell input text "Riya"; adb shell input keyevent KEYCODE_ENTER; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input swipe 540 1600 540 700 300; shot 02b-review-split 2
adb shell input keyevent KEYCODE_BACK; sleep 1
go money; shot 03-money
go capture todo; sleep 3; adb shell input text "Call%sbank%stomorrow%s11am"; shot 04-capture-todo 3
tap_text "+ Label"; sleep 1; tap_text "Work"; sleep 1; shot 04b-todo-labels 2
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; sleep 1
go capture ""; shot 05-capture-chooser
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'Toit Brewpub https://www.google.com/maps/place/Toit/@12.9790,77.6408,17z'" -n $PKG/.MainActivity
shot 06-share-place
go capture expense; sleep 2; adb shell input text "900"; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input swipe 540 1700 540 900 300; sleep 1
tap_text "Yes"; sleep 1
tap_text "Add a person's name"; sleep 1; adb shell input text "Riya"; adb shell input keyevent KEYCODE_ENTER; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input swipe 540 1700 540 900 300; shot 07-capture-expense 2
go saved; shot 08-saved
tap_text "Manage sections"; shot 08b-sections 2
adb shell input keyevent KEYCODE_BACK; sleep 1
go todos; shot 09-todos
go settings; shot 10-settings
adb shell input swipe 540 1500 540 700 300; shot 10b-settings-scrolled 2

# Learning mode: enable the accessibility service, start learning, switch apps, mark a payment
go learning; shot 11-learning-setup 4
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.watch.PayWatchService
adb shell settings put secure accessibility_enabled 1
sleep 3
go learning; sleep 3
tap_text "Start learning (3 hours)"; sleep 2
adb shell am start -a android.settings.SETTINGS; sleep 3
adb shell am start -a android.intent.action.VIEW -d "https://example.com" ; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 2
go learning; sleep 2
tap_text "Mark: Paid ✓"; sleep 2
tap_text "Preview the “Did you just pay…?” popup"; shot 11b-popup-preview 2
shot 12-learning-log 3
adb shell cmd statusbar expand-notifications; shot 13-learning-notification 3
adb shell cmd statusbar collapse

adb logcat -d > shots/logcat.txt
adb logcat -d | grep -iE "organiser|sms" | grep -iE "exception|error|denied" > shots/app-errors.txt || true
if grep -q "FATAL EXCEPTION" shots/logcat.txt; then
  echo "::error::App crashed"
  grep -A 30 "FATAL EXCEPTION" shots/logcat.txt | head -80
  exit 1
fi
echo "No crashes"
