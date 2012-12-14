<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="xml" indent="yes" 
        doctype-public="-//Sun Microsystems Inc.//DTD JavaHelp Map Version 1.0//EN"
        doctype-system="http://java.sun.com/products/javahelp/map_1_0.dtd">
    </xsl:output>
    <xsl:param name="context" select="''"/>
    <xsl:template match="/faq">
        <map version="1.0">
            <mapID target="openbook" url="../images/openbook.gif" />
            <mapID target="chapter" url="../images/chapter.gif" />
            <mapID target="home" url="index.html" />
            <mapID target="spec" url="tag-spec.html" />
            <mapID target="faq" url="faq.html" />
            <xsl:for-each select="group">
                <mapID target="faq_{position()}" url="faq_{position()}.html" />
            </xsl:for-each>
            <mapID target="usage" url="usage.html" />
            <mapID target="ant" url="ant.html" />
        </map>
    </xsl:template>

</xsl:stylesheet>
