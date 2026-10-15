$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac=Join-Path $jdk 'javac.exe'; $java=Join-Path $jdk 'java.exe'
if(!(Test-Path $javac)){$javac='javac';$java='java'}
$s=Get-Content "$root/app/src/main/java/io/github/yixing233/hyperduo/WifiPresence.java" -Raw
$m=[regex]::Match($s,'(?s)static boolean resolve\(boolean iconVisible, int cachedLevel\) \{.*?\n    \}').Value
if(!$m){throw 'Production resolver missing'}
$publish=[regex]::Match($s,'(?s)private static void publish\(boolean next\) \{.*?\n    \}').Value
if(!$publish){throw 'Production publish missing'}
if($s -match 'Context.RECEIVER_NOT_EXPORTED'){throw 'Wi-Fi UID broadcasts must be receivable'}
if($s.IndexOf('if (!enabled)') -gt $s.IndexOf('connectivity.getNetworkInfo')){throw 'Radio-off must bypass connectivity query'}
$out="$root/work/issue13/tests"; New-Item -ItemType Directory -Force $out | Out-Null
$test=@"
public class WifiTest {
 static Boolean connected;
 static class TrioState {
  static int sWifiLevel=4; static boolean present=true;
  static boolean setWifiPresent(boolean next){boolean changed=present!=next;present=next;return changed;}
 }
 static class TrioHooks {static int invalidations;static void invalidateHosts(){invalidations++;}}
 $m
 $publish
 public static void main(String[] args){
  int count=0;
  for(Boolean live:new Boolean[]{null,false,true})for(boolean icon:new boolean[]{false,true})for(int level:new int[]{-1,0,4}){
   connected=live;boolean want=live==null?(icon||level>=0):live;
   if(resolve(icon,level)!=want)throw new AssertionError("stale icon overrides live state");count++;
  }
  connected=false;if(resolve(true,4))throw new AssertionError("disabled wifi stale cache");
  connected=true;if(!resolve(false,-1))throw new AssertionError("reconnected wifi");
  connected=false;if(resolve(true,4))throw new AssertionError("disconnected again");
  publish(false);
  if(TrioState.sWifiLevel!=-1||TrioState.present||TrioHooks.invalidations!=1)throw new AssertionError("off must clear and repaint");
  publish(false);
  if(TrioHooks.invalidations!=1)throw new AssertionError("unchanged off must not repaint");
  publish(true);
  if(!TrioState.present||TrioHooks.invalidations!=2)throw new AssertionError("reconnect must repaint");
  System.out.println("PASS: "+count+" live/icon/cache cases and 3 reconnect transitions");
 }
}
"@
[System.IO.File]::WriteAllText("$out/WifiTest.java", $test, (New-Object System.Text.UTF8Encoding($false)))
& $javac -d $out "$out/WifiTest.java"; if($LASTEXITCODE){throw 'Compilation failed'}
& $java -Xmx64m -cp $out WifiTest; if($LASTEXITCODE){throw 'Test failed'}
