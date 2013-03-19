# Patterns for recognizing i18n strings in source code
# Each line is of the form
#   regexp keys
# If the regexp matches within a line of source text, the keys give the
# names of the implied i18n keys. Use the standard \int syntax for referring
# to capture groups within the regexp. Note the regular expression should
# not contain space characters. If necessary, use \s to match space.
# For spec of regular expressions, see java.util.regex.Pattern
# See also com.sun.jct.utils.i18ncheck.Main

# i18n.getString("...",
(printMessage|getI18NString|writeI18N|i18n.getString|formatI18N|setI18NTitle)\("([^"]*)"(,|\)) \2

# new BadArgs(i18n, "...",
(Message.get|Fault|BadArgs|BadValue|println|printErrorMessage|printMessage|[eE]rror|showMessage|popupError|write|JavaTestError|\.log|super)\((msgs|i18n),\s"([^"]*)"(,|\)) \3

# uif.createMessageArea("...",
uif.createMessageArea\("([^"]*)"(,|\)) \1.txt

# uif.showXXYZDialog("...",
uif.show(YesNo|YesNoCancel|OKCancel|Information|CustomInfo)Dialog\("([^"]*)"(,|\)) \2.txt \2.title

# uif.showWaitDialog("...",
uif.createWaitDialog\("([^"]*)"(,|\))  \1.txt \1.title \1.desc \1.name

# showError("...",
showError\("([^"]*)"(,|\)) \1.err

# new FileType()
new\sFileType\(\) filetype.allFiles

# new FileType("...")
new\sFileType\("([^"]*)"\) filetype\1

# i18n: ...
i18n:\s*(\S+) \1

