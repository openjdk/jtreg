<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet version="1.0"   
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:redirect="http://xml.apache.org/xalan/redirect"
    extension-element-prefixes="redirect" >
    <xsl:output method="html" indent="yes"/>
    <xsl:param name="basedir" select="'/tmp'"/>
    <xsl:param name="context" select="''"/>
    
    <xsl:template match="/faq">
        <xsl:for-each select="group">
            <xsl:variable name="groupIndex" select="position()"/>   
            <redirect:write file="{$basedir}/faq_{$groupIndex}.html">  
                <html>
                    <head>
                        <LINK TITLE="Style" HREF="help.css" TYPE="text/css" REL="stylesheet"/>
                        <title>
                            <xsl:value-of select="$groupIndex"/>.
                            <xsl:value-of select="@title"/>
                        </title>
                    </head>
                    <body>
                        <h1>
                            <xsl:value-of select="$groupIndex"/>.
                            <xsl:value-of select="@title"/>
                        </h1>
                        <ol>
                            <xsl:for-each select="entry[not(answer/@context) or answer/@context = $context]">
                                <li>
                                    <a href="faq_{$groupIndex}_{position()}.html"><xsl:copy-of select="question/node()"/></a>
                                </li>
                            </xsl:for-each>
                        </ol>
                    </body>
                </html>
            </redirect:write>
        </xsl:for-each>
        
        <xsl:for-each select="group">
            <xsl:variable name="groupIndex" select="position()"/>       
            <xsl:for-each select="entry[not(answer/@context) or answer/@context = $context]">
                <redirect:write file="{$basedir}/faq_{$groupIndex}_{position()}.html">
                    <html>
                        <head>
                            <LINK TITLE="Style" HREF="help.css" TYPE="text/css" REL="stylesheet"/>
                            <title>
                                <xsl:value-of select="$groupIndex"/>.<xsl:value-of select="position()"/>.
                                <xsl:copy-of select="question/text()"/>
                            </title>
                        </head>
                        <body>
                            <h1><xsl:value-of select="$groupIndex"/>.<xsl:value-of select="position()"/>.
                                <xsl:copy-of select="question/node()"/>
                            </h1>
                            <xsl:copy-of select="answer[not(@context) or @context = $context]/node()"/>
                        </body>
                    </html>
                </redirect:write>
            </xsl:for-each>
        </xsl:for-each>
        
        <html>
            <head>
                <LINK TITLE="Style" HREF="help.css" TYPE="text/css" REL="stylesheet"/>
                <title>The "Regression Test Harness for the OpenJDK platform: jtreg" FAQ</title>
            </head>
            <body>
                <h1>The "Regression Test Harness for the OpenJDK platform: jtreg" FAQ</h1>
                
                <p>This FAQ is a growing list of questions asked by developers writing tests
                which will run using the regression test harness for the OpenJDK platform, jtreg.
                It is a supplement to the test-tag language <a href="tag-spec.html">specification</a>
                and is intended to illuminate implications of the spec and to answer questions
                about this implementation of the spec.</p>

                <ol>
                    <xsl:for-each select="group">
                        <li><a href="faq_{position()}.html"><xsl:value-of select="@title"/></a></li>
                    </xsl:for-each>
                </ol>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
