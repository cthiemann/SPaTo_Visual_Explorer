<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>

<head>
  <title>SPaTo Visual Explorer - Release Notes</title>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
  <style type="text/css">
  body {
    background: #f5f5f5;
    font-family: "Hoefler Text", "Times New Roman", "Times", serif;
    <?php if (isset($_REQUEST["font-size"])) echo ("font-size: ".$_REQUEST["font-size"].";"); ?>
    line-height: 120%;
  }
  h1, h2, h3, h4, h5, .release .header {
    font-family: "Gill Sans", Helvetica, Arial, sans-serif;
  }
  a { text-decoration: none; color: #3875d7;}
  .release-divider { margin: 1.5em 1em; border-bottom: 1px dotted silver; }
  .release .header { margin: 2em 0 1.5em 0; }
  .release .header .version { font-weight: bold; font-size: 110%; }
  .release.patch .header .version { color: #555555; }
  .release .header .debug { margin: 0 1em; }
  .release .header .debug.alpha { color: red; }
  .release .header .debug.beta { color: orange; }
  .release .header .date { color: #555555; }
  .release .body { margin-left: 1em; }
  .release .note { margin: .5em 0; }
  .release .list { margin: 1em 1em; padding-left: 1em; }
  .release .list .item { margin: .5em 0; }
  .release .list .item.new { color: #000055; }
  .release .list .item.enh { color: #555500; }
  .release .list .item.fix { color: #550000; }
  .link-to-full-notes { margin: 2em 1em; }
  </style>
</head>

<body>

<h2>Release Notes</h2> 

<?php

function cmpVersion($v1, $v2) {
  $v1 = explode('.', $v1); $v2 = explode('.', $v2);
  if ($v1[0] != $v2[0])  // major version is different
    return 1*$v1[0] - 1*$v2[0];
  if ($v1[1] != $v2[1])  // minor version is different
    return 1*$v1[1] - 1*$v2[1];
  // split into dev-release and patch number...
  $v1 = explode('_', $v1[2]); $v2 = explode('_', $v2[2]);
  if (count($v1) < 2) $v1[1] = 0;
  if (count($v2) < 2) $v2[1] = 0;
  // ...and continue
  if ($v1[0] != $v2[0])  // dev-release is different
    return 1*$v1[0] - 1*$v2[0];
  return 1*$v1[1] - 1*$v2[1];
}

date_default_timezone_set("UTC");

$RELEASE_NOTES = simplexml_load_file("RELEASE_NOTES.xml");

$requestVersion = isset($_REQUEST['version']) ? $_REQUEST['version'] : "0.0.0_00";
$releaseNotesURL = $_SERVER["PHP_SELF"];

$first = true;
foreach ($RELEASE_NOTES->release as $release) {
  $version = $release['version'];
  if (cmpVersion($version, $requestVersion) <= 0) {
    echo("<p class=\"link-to-full-notes\"><a href=\"$releaseNotesURL#release-$version\">".
         "View release notes for earlier versions</a></p>");
    break;
  }
  $rtype = strstr($version, '_') ? "patch" : "release";
  $debug = $release['debug'];
  $date = date("F j, Y", strtotime($release['date']));
  if (!$first)
    echo("<div class=\"release-divider\"><a name=\"release-$version\"></a></div>\n");
  echo("<div class=\"release $rtype\">\n");
  echo("  <div class=\"header\">".
         "<span class=\"version\">$version</span>".
         "<span class=\"debug $debug\">$debug</span>".
         "<span class=\"date\">$date</span>".
       "</div>\n");
  echo("  <div class=\"body\">\n");
  if ($release->note)
    echo("    <p class=\"note\">".$release->note->asXML()."</p>\n");
  echo("    <ul class=\"list\">\n");
  foreach ($release->item as $item)
    echo("      <li class=\"item ".$item['type']."\">".$item->asXML()."</li>\n");
  echo("    </ul>\n");
  echo("  </div>\n\n");
  echo("</div>\n\n");
  $first = false;
}

?>

</body>

</html>