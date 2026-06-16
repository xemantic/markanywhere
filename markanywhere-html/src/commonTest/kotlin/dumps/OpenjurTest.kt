/*
 * Copyright 2026 Kazimierz Pogoda / Xemantic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xemantic.markanywhere.html.dumps

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.html.DumpFixtures
import com.xemantic.markanywhere.html.dumpFlow
import com.xemantic.markanywhere.html.transformHtmlToMarkdown
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OpenjurTest {

    @Test
    fun `should convert captured openjur DOM dump to Markdown`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.openjur)

        // when
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs /* language=markdown */ """
            ---
            lang: de
            title: KG, Urteil vom 15.09.2021 - 5 U 35/20 - openJur
            description: A. Auf die Berufung des Klägers und unter ihrer Zurückweisung im Übrigen wird das am 11. Februar 2020 verkündete Urteil der Zivilkammer 16 des Landgerichts Berlin - 16 O 175/19 - a ...
            keywords: 5 u 35/20, 15.09.2021, kg, urteil, kammergericht
            author: openJur gGmbH
            ---
            
            <header>
            <nav>
            
            [![openJur](https://cdn.openjur.net/img/logo.svg)](ref:1:/)
            
            - [Rechtsprechung](ref:2:#)
            - [Informationen](ref:3:#)
            
            <form action="/suche/" method="post">
            <input id="searchPhrase" type="search" name="searchPhrase" placeholder="openJur durchsuchen" ref="4">
            <button id="button-searchPhrase" type="submit" ref="5">
            
            Suchen!
            
            </button>
            </form>
            </nav>
            </header>
            <main>
            <nav aria-label="breadcrumb">
            
            1. [Startseite](ref:6:/)
            2. [Berlin](ref:7:/be/)
            3. [Ordentliche Gerichtsbarkeit](ref:8:/be/og.html)
            4. [KG](ref:9:/be/kg.html)
            5. [Rechtsprechung](ref:10:/be/kg-rspr.html)
            6. Urteil vom 15.09.2021 - 5 U 35/20
            
            </nav>
            
            ###### [KG](ref:11:/be/kg.html), Urteil vom 15.09.2021 - 5 U 35/20
            
            Fundstelle openJur 2022, 4663 Rechtskraft: ❓ 
            
            <button type="button" aria-expanded="false" ref="12">
            
            Optionen
            
            </button>
            
             [Datenschutzrecht](ref:13:/gebiet-44.html) [Zivilrecht](ref:14:/gebiet-1.html) 
            
            ###### Tenor
            
            A.
            
            Auf die Berufung des Klägers und unter ihrer Zurückweisung im Übrigen wird das am 11. Februar 2020 verkündete Urteil der Zivilkammer 16 des Landgerichts Berlin - [16 O 175/19](ref:15:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=16%20O%20175/19) - abgeändert und wie folgt neu gefasst:
            
            I.
            
            Die Beklagte wird verurteilt, es zu unterlassen, den Kläger telefonisch zu werblichen Zwecken zu kontaktieren oder kontaktieren zu lassen, ohne dass die vorherige ausdrückliche Einwilligung des Klägers vorliegt, wenn dies geschieht wie durch den Anruf der Beklagten vom 22. März 2019, bei dem die Beklagte den Kläger um Teilnahme an einer Zufriedenheitsbefragung bat und anfragte, ob der Kläger überhaupt bereit wäre, sich für solche Rückfragen 2/3 Minuten Zeit zu nehmen. Der Beklagten wird für jeden Fall der Zuwiderhandlung gegen die Unterlassungsverpflichtung ein Ordnungsgeld bis zur Höhe von 250.000,00 €, ersatzweise Ordnungshaft, oder Ordnungshaft bis zu 6 Monaten angedroht, wobei die Ordnungshaft an den jeweiligen Vorständen der Beklagten zu vollziehen ist.
            
            II.
            
            Die Beklagte wird verurteilt, es zu unterlassen, an den Kläger Werbung per elektronischer Post zu versenden oder versenden zu lassen, ohne dass die vorherige ausdrückliche Einwilligung des Klägers vorliegt, wenn dies geschieht, wie aus den E-Mails der Beklagten vom 25. März 2019 (Anlage K8) sowie 4. April 2019 (Anlage K4) ersichtlich. Der Beklagten wird für jeden Fall der Zuwiderhandlung gegen die Unterlassungsverpflichtung ein Ordnungsgeld bis zur Höhe von 250.000,00 €, ersatzweise Ordnungshaft, oder Ordnungshaft bis zu 6 Monaten angedroht, wobei die Ordnungshaft an den jeweiligen Vorständen der Beklagten zu vollziehen ist.
            
            III.
            
            Es wird festgestellt, dass sich der Rechtsstreit in der Hauptsache teilweise, nämlich hinsichtlich des erstinstanzlich gestellten Antrag des Klägers, die Beklagte zur Auskunft gem. Art. [15](ref:16:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit c) DS-GVO zu verurteilen, erledigt hat.
            
            IV.
            
            Im Übrigen wird die Klage abgewiesen.
            
            B.
            
            Von den Kosten des Rechtsstreits beider Instanzen tragen jeweils der Kläger 11 % und die Beklagte 89 %.
            
            C.
            
            Das Urteil ist vorläufig vollstreckbar.
            
            Die Beklagte kann die Vollstreckung gegen sich abwenden aus dem Tenor zu I. durch Sicherheitsleistung in Höhe von 6.000,00 € und aus dem Tenor zu II. durch Sicherheitsleistung in Höhe von 6.600,00 €, wenn nicht der Kläger jeweils vor der Vollstreckung Sicherheit in gleicher Höhe leistet.
            
            Die Beklagte kann im Übrigen die Vollstreckung gegen sich abwenden durch Sicherheitsleistung in Höhe von 110% des gegen sie vollstreckbaren Betrages, wenn nicht der Kläger vor der Vollstreckung Sicherheit in Höhe von 110% des jeweils durch ihn beizutreibenden Betrages leistet.
            
            Der Kläger kann die Vollstreckung gegen sich abwenden durch Sicherheitsleistung in Höhe von 110% des gegen ihn vollstreckbaren Betrages, wenn nicht die Beklagte vor der Vollstreckung Sicherheit in Höhe von 110% des jeweils durch sie beizutreibenden Betrages leistet.
            
            D.
            
            Die Revision wird zugelassen.
            
            ###### Gründe
            
            I.
            
            Gemäß § [540](ref:17:https://dejure.org/gesetze/ZPO/540.html) Abs. 1 Satz 1 Nr. 1 ZPO wird auf die tatsächlichen Feststellungen im angegriffenen Urteil des Landgerichts Bezug genommen. Ergänzend wird ausgeführt:
            
            Mit Schriftsatz vom 11. Dezember 2019 (dort S. 9, Bl. I/65 d. A.) hat die Beklagte ergänzend im Hinblick auf Art. [15](ref:18:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 Satz 1 HS. 2 lit. c) DS-GVO vorgetragen. Von diesem Schriftsatz hat der Kläger im Termin zur mündlichem Verhandlung vor dem Landgericht am 17. Dezember 2019 Kenntnis erhalten. Auf seinen Antrag hat das Landgericht dem Kläger eine "Erklärungsfrist auf den Schriftsatz der Beklagten vom 11. Dezember 2019" gewährt (S. 2 der Sitzungsniederschrift, Bl. I/67 d. A.). Mit Schriftsatz vom 23. Dezember 2019, der innerhalb dieser Erklärungsfrist beim Landgericht eingegangen ist, hat der Kläger vorgetragen, soweit die Beklagte über die Empfänger und Kategorien von Empfängern im Schriftsatz vom 11. Dezember 2019 mitgeteilt habe, sei Teilerledigung eingetreten (dort Rz. 34, Bl. I/86 d. A.). Die Beklagte hat sich der Teilerledigungserklärung nicht angeschlossen (vgl. Schriftsatz der Beklagten vom 21. Januar 2020, dort S. 6, Bl. I/94 d. A., sowie Berufungserwiderung S. 3 unter Rz. 2.3, Bl. II/43 d. A.).
            
            Das Landgericht hat mit am 11. Februar 2020 verkündetem Urteil die Klage abgewiesen. Mit seiner Berufung verfolgt der Kläger seine vor dem Landgericht erfolglos gebliebenen Anträge modifiziert weiter. Er wiederholt, vertieft und ergänzt sein erstinstanzliches Vorbringen und stützt die von ihm geltend gemachten Ansprüche auf Ersatz vorgerichtlich entstandener Kosten (Berufungsanträge zu 2, 4 und 6) nunmehr auch auf Art. [82](ref:19:https://dejure.org/gesetze/DSGVO/82.html) DS-GVO. Schließlich rügt der Kläger, dass das Landgericht keine Entscheidung getroffen habe, soweit er den erstinstanzlich zu 5) geltend gemachten Antrag teilweise für erledigt erklärt hat.
            
            Der Kläger beantragt zuletzt,
            
            1.
            
            Der Beklagten wird unter Androhung von Ordnungsmitteln für jeden Fall der Zuwiderhandlung gegen die Unterlassungsverpflichtung untersagt, den Kläger telefonisch zu werblichen Zwecken zu kontaktieren oder kontaktieren zu lassen, ohne dass die vorherige ausdrückliche Einwilligung des Klägers vorliegt, wenn dies geschieht wie durch den Anruf der Beklagten vom 22. März 2019, bei dem die Beklagte den Kläger um Teilnahme an einer Zufriedenheitsbefragung bat und anfragte, ob der Kläger überhaupt bereit wäre, sich für solche Rückfragen zwei/drei Minuten Zeit zu nehmen.
            
            2.
            
            Die Beklagte wird weiter verurteilt, an den Kläger 571,44 € nebst Zinsen in Höhe von 5 Prozentpunkten über dem jeweiligen Basiszinssatz seit dem 12. Juni 2019 zu zahlen.
            
            3.
            
            Der Beklagten wird unter Androhung von Ordnungsmitteln für jeden Fall der Zuwiderhandlung gegen die Unterlassungsverpflichtung untersagt, an den Kläger Werbung per elektronischer Post zu versenden oder versenden zu lassen, ohne dass die vorherige ausdrückliche Einwilligung des Klägers vorliegt, wenn dies geschieht, wie aus den E-Mails der Beklagten vom 25. März 2019 (Anlage K8) sowie 4. April 2019 (Anlage K2) ersichtlich.
            
            4.
            
            Die Beklagte wird weiter verurteilt, an den Kläger Kosten der Abmahnung vom 22. April 2019 in Höhe von 571,44 € nebst Zinsen in Höhe von 5 Prozentpunkten über dem jeweiligen Basiszinssatz seit dem 18. November 2019 zu zahlen.
            
            5.
            
            Die Beklagte wird weiter verurteilt, an den Kläger gemäß Art. 15 Abs. 1 Halbsatz 2 lit. h) Datenschutz - Grundverordnung (DS-GVO) Auskunft über das Bestehen (oder Nichtbestehen) einer automatisierten Entscheidungsfindung einschließlich Profiling gemäß Art. [22](ref:20:https://dejure.org/gesetze/DSGVO/22.html) Absätze 1 und [4](ref:21:https://dejure.org/gesetze/DSGVO/4.html) DS-GVO und - zumindest in diesen Fällen - aussagekräftige Informationen über die involvierte Logik sowie die Tragweite und die angestrebten Auswirkungen einer derartigen Verarbeitung für den Kläger.
            
            6.
            
            Die Beklagte wird verurteilt, an den Kläger 255,85 € vorgerichtliche Kosten als Verzugsschaden (hinsichtlich des Klageantrags zu 5) nebst Zinsen in Höhe von 5 Prozentpunkten über dem jeweiligen Basiszinssatz seit dem 18. November 2019 zu zahlen.
            
            Die Beklagte beantragt,
            
            die Berufung zurückzuweisen.
            
            Die Beklagte tritt dem Vorbringen des Klägers entgegen und verteidigt das erstinstanzliche Urteil. Hinsichtlich des Klageantrages zu 3) erhebt sie die Einrede der Verjährung
            
            Wegen der weiteren Einzelheiten wird auf die in zweiter Instanz eingereichten Schriftsätze nebst Anlagen der Parteien (Bl. I/155 - 209, II/41 - 49, 61 - 67, 70 - 86, 92 - 115 d. A.) sowie auf das Protokoll der mündlichen Verhandlung vom 15. September 2021 Bezug genommen.
            
            II.
            
            A.
            
            Die Berufung ist gemäß §§ [511](ref:22:https://dejure.org/gesetze/ZPO/511.html), [517](ref:23:https://dejure.org/gesetze/ZPO/517.html), [519](ref:24:https://dejure.org/gesetze/ZPO/519.html), [520](ref:25:https://dejure.org/gesetze/ZPO/520.html) ZPO form- und fristgerecht eingelegt, mit einer Begründung versehen und auch im Übrigen zulässig.
            
            B.
            
            In der Sache hat die Berufung teilweise Erfolg, sodass insoweit die landgerichtliche Entscheidung abzuändern ist. Im Übrigen unterliegt die Berufung der Zurückweisung.
            
            1. Berufungsantrag zu 1)
            
            Der Kläger kann von der Beklagten Unterlassung von Telefonanrufen mit werblichem Inhalt, wie aus dem Tenor zu I. ersichtlich, verlangen und sich dabei auf § [823](ref:26:https://dejure.org/gesetze/BGB/823.html) Abs. 1, § [1004](ref:27:https://dejure.org/gesetze/BGB/1004.html) Abs. 1 S. 2 BGB analog berufen.
            
            a) Ein Anruf bei einem Unternehmer zu Werbezwecken stellt, wenn keine tatsächliche oder mutmaßliche Einwilligung vorliegt, einen Eingriff in den von § [823](ref:28:https://dejure.org/gesetze/BGB/823.html) Abs. 1, § [1004](ref:29:https://dejure.org/gesetze/BGB/1004.html) Abs. 1 S. 2 BGB geschützten eingerichteten und ausgeübten Gewerbebetrieb dar.
            
            aa) Gegenstand des Schutzes ist die Verhinderung des Eindringens des Werbenden in die geschäftliche Sphäre, insbesondere die Ungestörtheit der Betriebsabläufe des Empfängers; es soll verhindert werden, dass diesem Werbemaßnahmen gegen seinen erkennbaren und mutmaßlichen Willen aufgedrängt werden (vgl. BGH, Urteil vom 21. April 2016 - [I ZR 276/14](ref:30:/u/889447.html) -, Rn. 16, juris - Lebens-Kost). Verhindert werden soll darüber hinaus, dass die belästigende Werbung zu einer Bindung von Ressourcen des Angerufenen, wie z. B. Zeitaufwand, führt (BGH, Urteil vom 01. Juni 2006 - [I ZR 167/03](ref:31:/u/80641.html) -, Rn. 9, juris - Telefax-Werbung II).
            
            bb) So liegt es hier hinsichtlich des Anrufes der Beklagten vom 22. März 2019 (nachfolgend auch nur "streitgegenständlicher Anruf").
            
            (1) Der streitgegenständliche Anruf hatte einen werblichen Inhalt.
            
            (a) Der Begriff der Werbung umfasst nach dem allgemeinen Sprachgebrauch alle Maßnahmen eines Unternehmens, die auf die Förderung des Absatzes seiner Produkte oder Dienstleistungen gerichtet sind. Damit ist außer der unmittelbar produktbezogenen Werbung auch die mittelbare Absatzförderung - beispielsweise in Form der Imagewerbung - erfasst. Werbung ist deshalb in Übereinstimmung mit Art. 2 lit. a der Richtlinie 2006/114/EG des Europäischen Parlaments und des Rates vom 12. Dezember 2006 über irreführende und vergleichende Werbung jede Äußerung bei der Ausübung eines Handels, Gewerbes, Handwerks oder freien Berufs mit dem Ziel, den Absatz von Waren oder die Erbringung von Dienstleistungen zu fördern (vgl. BGH, Urteil vom 15. Dezember 2015 - [VI ZR 134/15](ref:32:/u/870114.html) -, Rn. 16, juris; Urteil vom 12. September 2013 - [I ZR 208/12](ref:33:/u/653396.html) -, Rn. 17, juris - Empfehlungs-E-Mail). Insbesondere Kundenzufriedenheitsabfragen dienen zumindest auch dazu, so befragte Kunden an sich zu binden und künftige Geschäftsabschlüsse zu fördern. Durch derartige Befragungen wird dem Kunden der Eindruck vermittelt, der fragende Unternehmer bemühe sich auch nach Geschäftsabschluss um ihn. Der Unternehmer bringt sich zudem bei dem Kunden in Erinnerung, was der Kundenbindung dient und eine Weiterempfehlung ermöglicht. Damit soll auch weiteren Geschäftsabschlüssen der Weg geebnet und hierfür geworben werden (BGH, Urteil vom 10. Juli 2018 - [VI ZR 225/17](ref:34:/u/2111609.html) -, Rn. 18, juris).
            
            (b) Schon aus der E-Mail der Beklagten vom 04. April 2019 (Anlage K 4) ergibt sich, dass Gegenstand des streitgegenständlichen Anrufes eine Kundenzufriedenheitsabfrage im obigen Sinne war. Nach den dortigen Ausführungen der Beklagten wollte die Beklagte im Rahmen des Telefonates erfahren, wie zufrieden der Kläger mit dem Kundenservice der Beklagten gewesen sei. Selbst wenn man den weiteren Vortrag der Beklagten als wahr unterstellt, wonach sich die Beklagte beim Kläger in dem Telefonat zudem für die ihm entstandenen Unannehmlichkeiten entschuldigen wollte, hätte dies zumindest auch dazu gedient, künftige Geschäftsabschlüsse zu fördern, da sich die Beklagte auch auf diesem Wege bei dem Kläger in Erinnerung gebracht hätte.
            
            (2) Der Anruf erfolgte zudem während der üblichen Geschäftszeiten auf einem Telefonanschluss, den der Kläger beruflich nutzt, wie zwischenzeitlich auch die Beklagte vorträgt (S. 2 des Schriftsatzes der Beklagten vom 21. Januar 2020, Bl. I/91 d. A.).
            
            b) Der Eingriff in den eingerichteten und ausgeübten Gewerbebetrieb des Klägers ist auch rechtswidrig.
            
            Das Recht des Klägers am eingerichteten und ausgeübten Gewerbebetrieb, das durch Art. [12](ref:35:https://dejure.org/gesetze/GG/12.html) GG verfassungsrechtlich gewährleistet ist (vgl. nur BGH, Urteil vom 15. Januar 2019 - [VI ZR 506/17](ref:36:/u/2134901.html) -, Rn. 15, juris), ist mit dem berechtigten Interesse der Beklagten, mit ihren Kunden zum Zwecke der Werbung in Kontakt zu treten, abzuwägen. Denn wegen der Eigenart des erstgenannten Rechts als eines Rahmenrechts liegt seine Reichweite nicht absolut fest, sondern muss erst durch eine Abwägung der widerstreitenden Belange bestimmt werden (vgl. nur BGH, Urteil vom 15. Dezember 2015 - [VI ZR 134/15](ref:37:/u/870114.html) -, Rn. 21, juris). Diese Abwägung, bei der die Maßstäbe des § [7](ref:38:https://dejure.org/gesetze/UWG/7.html) UWG zur Vermeidung von Wertungswidersprüchen zur Anwendung kommen (BGH, Urteil vom 21. April 2016 - [I ZR 276/14](ref:39:/u/889447.html) -, Rn. 16, juris - Lebens-Kost), geht zu Lasten der Beklagten aus, wie der Wertung des § [7](ref:40:https://dejure.org/gesetze/UWG/7.html) Abs. 2 UWG zu entnehmen ist (vgl. BGH, Urteil vom 14. März 2017 - [VI ZR 721/15](ref:41:/u/963084.html) -, Rn. 28, juris).
            
            aa) Nach § [7](ref:42:https://dejure.org/gesetze/UWG/7.html) Abs. 2 Nr. 2 UWG ist eine unzumutbare Belästigung stets anzunehmen bei Werbung mit einem Telefonanruf gegenüber einem Verbraucher ohne dessen vorherige ausdrückliche Einwilligung oder gegenüber einem sonstigen Marktteilnehmer ohne dessen zumindest mutmaßliche Einwilligung.
            
            bb) Wie das Landgericht zutreffend angenommen hat, ist der Kläger ein "sonstiger Marktteilnehmer" iSd. genannten Vorschrift, sodass schon eine mutmaßliche Einwilligung ausreichend wäre. Selbst eine solche mutmaßliche Einwilligung des Klägers in die Telefonwerbung ist hier aber nicht gegeben.
            
            (1) Das Vorliegen einer mutmaßlichen Einwilligung ist anhand der Umstände vor dem Anruf sowie anhand der Art und des Inhalts der Werbung festzustellen. Erforderlich ist, dass aufgrund konkreter tatsächlicher Umstände ein sachliches Interesse des Anzurufenden an der Telefonwerbung vermutet werden kann. Maßgeblich ist, ob der Werbende bei verständiger Würdigung der Umstände annehmen durfte, der Anzurufende erwarte einen solchen Anruf oder werde ihm jedenfalls aufgeschlossen gegenüberstehen (vgl. nur BGH, Urteil vom 11. März 2010 - [I ZR 27/08](ref:43:/u/192654.html) -, Rn. 20 - 21, juris - Telefonwerbung nach Unternehmenswechsel; Köhler in: Köhler/Bornkamm/Feddersen, UWG, 39. Aufl., § 7 Rn. 163 f.).
            
            (2) Diese Voraussetzungen sind vorliegend jedenfalls deswegen nicht erfüllt, da keine mutmaßliche Einwilligung in eine Telefonwerbung und damit in die von der Beklagten gewählte Art der Werbung gegeben ist. Eine bei der Beklagten eingelieferte Briefsendung war in Verlust geraten, und die Beklagte hatte angekündigt, das vom Kläger für die Beförderung entrichtete Entgelt sowie die hierfür vorgesehene (aus Sicht vieler Kunden geringe) Entschädigung zu zahlen. Wieso der Kläger vor diesem Hintergrund gerade dem von der Beklagten behaupteten Zweck des Telefonanrufes gegenüber aufgeschlossen sein sollte, spontan und telefonisch unter Einsatz seiner Arbeitszeit den Kundenservice der Beklagten zu bewerten und so die Grundlage zu schaffen für eine nicht den Kunden aktuell, sondern allenfalls zukünftige Kunden betreffende Verbesserung des Kundenservices, ist nicht ersichtlich. Der Kläger hatte sich zuvor gerade nicht telefonisch, sondern mittels eines Online-Formulars an die Beklagte gewandt. Die angerufene Telefonnummer hatte er auf dem Formular "Nachforschungsauftrag International" angegeben, das keine Hinweise darauf bietet, dass die Telefonnummer für Zufriedenheitsanfragen genutzt werden könnte. Es ist auch nicht ersichtlich, aus welchen Gründen eine Zufriedenheitsbefragung unbedingt telefonisch durchgeführt werden müsste.
            
            cc) Es liegen hier auch keine besonderen Umstände vor, die die Vermutung der Rechtswidrigkeit ausräumen könnten.
            
            (1) Für die von der Beklagten auf S. 7 der Klageerwiderung geforderte (angeblich) "richtlinienkonforme Auslegung" des § [7](ref:44:https://dejure.org/gesetze/UWG/7.html) Abs. 2 Nr. 2 UWG im Hinblick auf die Richtlinie 2005/29/EG ist kein Platz. Nach der Rechtsprechung des BGH kommt Art. 13 Abs. 3 RL 2002/58/EG und damit § [7](ref:45:https://dejure.org/gesetze/UWG/7.html) Abs. 2 Nr. 2 UWG Vorrang zu vor Nr. 26 Anhang I RL 2005/29/EG über unlautere Geschäftspraktiken; letztgenannte Vorschrift lässt die Anwendung der Vorschriften der RL 2002/58/EG ausdrücklich unberührt (vgl. nur BGH, Urteil vom 10. Februar 2011 - [I ZR 164/09](ref:46:/u/168642.html) -, Rn. 24 ff., juris - Double-opt-in-Verfahren; Urteil vom 14. Januar 2016 - [I ZR 65/14](ref:47:/u/870838.html) -, Rn. 24, juris - Freunde finden).
            
            (2) Auch wenn der Telefonanruf nur kurz gedauert haben mag, musste der Kläger für die Entgegennahme seinen Arbeitsablauf unterbrechen und sich mit dem Anruf beschäftigen. Damit hat sich im vorliegenden Fall die typische Belästigungswirkung eines Anrufes mit werblichen Inhalten verwirklicht.
            
            c) Die für den Unterlassungsanspruch erforderliche Wiederholungsgefahr wird durch das festgestellte rechtsverletzende Verhalten der Beklagten indiziert und ist von ihr, die keine strafbewehrte Unterlassungserklärung abgegeben hat, nicht ausgeräumt worden (BGH, Urteil vom 10. Juli 2018 - [VI ZR 225/17](ref:48:/u/2111609.html) -, Rn. 29, juris; Urteil vom 12. September 2013 - [I ZR 208/12](ref:49:/u/653396.html) -, Rn. 25 f., juris - Empfehlungs-E-Mail; Köhler in: Köhler/Bornkamm/Feddersen, UWG, 39. Aufl., § 7 Rn. 119).
            
            d) Der Antrag des Klägers ist auch nicht zu weit gefasst, obwohl er - entgegen § [7](ref:50:https://dejure.org/gesetze/UWG/7.html) Abs. 2 Nr. 2 Alt. 2 UWG - auch Anrufe verbieten lassen möchte, bei denen eine zumindest mutmaßliche Einwilligung vorliegt. Im vorliegenden Fall ist dies zulässig, da angesichts der unzweideutigen Erklärungen des Klägers, er wolle von der Beklagten unter keinen Umständen angerufen werden, eine mutmaßliche Einwilligung von vornherein ausscheidet.
            
            2. Berufungsantrag zu 3)
            
            Der Kläger kann von der Beklagten Unterlassung von E-Mails mit werblichem Inhalt, wie aus dem Tenor zu II. ersichtlich, verlangen und sich dabei auf § [823](ref:51:https://dejure.org/gesetze/BGB/823.html) Abs. 1, § [1004](ref:52:https://dejure.org/gesetze/BGB/1004.html) Abs. 1 S. 2 BGB analog berufen.
            
            a) Die Zusendung von elektronischer Post an einen Adressaten, der das Postfach beruflich nutzt, für Zwecke der Werbung ohne dessen Einwilligung stellt einen Eingriff in den von § [823](ref:53:https://dejure.org/gesetze/BGB/823.html) Abs. 1, § [1004](ref:54:https://dejure.org/gesetze/BGB/1004.html) Abs. 1 S. 2 BGB geschützten eingerichteten und ausgeübten Gewerbebetrieb dar.
            
            aa) Hinsichtlich des Gegenstandes des Schutzes kann auf die obigen Ausführungen zur Telefonwerbung verwiesen werden. Unverlangt zugesendete E-Mail-Werbung erfolgt betriebsbezogen und beeinträchtigt den Betriebsablauf im Unternehmen des Empfängers (BGH, Urteil vom 14. März 2017 - [VI ZR 721/15](ref:55:/u/963084.html) -, Rn. 15, juris).
            
            bb) So liegt es hier hinsichtlich der E-Mails der Beklagten vom 25. März 2019 und 04. April 2019 (nachfolgend auch nur "streitgegenständliche E-Mails"), die die Beklagte an ein Postfach des Klägers geschickt hat, das dieser auch beruflich nutzt.
            
            (1) Diese E-Mails beinhalteten an deren jeweiligen Ende jeweils ein werbliches Element:
            
            XXXXX. Organisiert, denkt mit, erledigt.Nutzen Sie www.XXXXX.de
            
            (2) Zwar ist der übrige, weit überwiegende Teil der streitgegenständlichen E-Mails keine Werbung. Dies hat aber nicht zur Folge, dass das werbliche Element von vornherein keine Werbung darstellen könnte. Die streitgegenständlichen E-Mails werden von der Beklagten vielmehr in zweifacher Hinsicht - nämlich für die nicht zu beanstandende Kommunikation im Rest der E-Mails und ganz am Ende für Zwecke der Werbung - genutzt. Nach der Rechtsprechung des BGH ist in solchen Konstellationen für die Annahme, die Nutzung der elektronischen Post sei durch den zulässigen Teil der E-Mail insgesamt gerechtfertigt, "kein Raum" (BGH, Urteil vom 10. Juli 2018 - [VI ZR 225/17](ref:56:/u/2111609.html) -, Rn. 20, juris; Urteil vom 15. Dezember 2015 - [VI ZR 134/15](ref:57:/u/870114.html) -, Rn. 19, juris).
            
            b) Der Eingriff in den eingerichteten und ausgeübten Gewerbebetrieb des Klägers ist auch rechtswidrig. Die insoweit erforderliche Abwägung, bei der die Maßstäbe des § [7](ref:58:https://dejure.org/gesetze/UWG/7.html) UWG zur Vermeidung von Wertungswidersprüchen zur Anwendung kommen (vgl. nur BGH, Urteil vom 21. April 2016 - [I ZR 276/14](ref:59:/u/889447.html) -, Rn. 16, juris - Lebens-Kost), geht zu Lasten der Beklagten aus.
            
            aa) Die Beklagte hat zwar nicht von der Hand zu weisende Argumente vorgebracht, die gegen eine Rechtswidrigkeit streiten: So sei hier zu berücksichtigen, dass der werbliche Zusatz
            
            - durch einen Absatz von dem Rest der E-Mail abgesetzt sei,
            
            - flächenmäßig einen Bruchteil der gesamten E-Mail ausmache und lediglich aus acht Worten bestehe,
            
            - am Ende der Nachricht stehe,
            
            - nicht in einem Zusammenhang mit dem Rest der E-Mail stehe und nicht als Teil des Schreibens angesehen werden könne.
            
            Zudem seien die E-Mails ohne Anhang versendet worden und das Laden und die Inanspruchnahme von Speicherkapazität habe sich auf ein Minimum beschränkt. Dadurch habe der werbliche Zusatz sowohl aufgrund der Gestaltung der E-Mails als auch aufgrund des Inhaltes leicht als werbend identifiziert werden und leicht unbeachtet bleiben können. Der Zeitaufwand für die Identifizierung des Zusatzes als Werbeteil habe sich auf ein Minimum beschränkt. Damit sei die Beeinträchtigung des Klägers in einem so minimalen Maße eingetreten, dass eine Abwägung der widerstreitenden Interessen zugunsten der Beklagten ausfallen müsse.
            
            bb) Trotz dieser aus Sicht des Senates nicht von der Hand zu weisenden Argumente muss unter Beachtung der Rechtsprechung des BGH die Abwägung zu Lasten der Beklagten ausgehen.
            
            (1) Nach Ansicht des BGH (Urteil vom 10. Juli 2018 - [VI ZR 225/17](ref:60:/u/2111609.html) -, Rn. 25, juris) reicht es nicht aus, wenn die unerwünschte Werbung die Interessen des Klägers nur vergleichsweise geringfügig beeinträchtigt. Das Hinzufügen von Werbung zu einer im Übrigen zulässigen E-Mail-Nachricht sei, so der BGH, allerdings keine solche Bagatelle, dass eine Belästigung des Nutzers ausgeschlossen wäre, denn zumindest müsse sich der Nutzer gedanklich mit den werblichen Elementen beschäftigen. "Zwar mag sich der Arbeitsaufwand bei einer einzelnen E-Mail in Grenzen halten. Mit der häufigen Verwendung von Werbezusätzen ist aber immer dann zu rechnen, wenn die Übermittlung einzelner E-Mails mit solchen Zusätzen zulässig ist. Denn im Hinblick auf die billige, schnelle und durch Automatisierungsmöglichkeit arbeitssparende Versendungsmöglichkeit und ihrer günstigen Werbewirkung ist mit einem Umsichgreifen dieser Werbeart zu rechnen. Eine bei isolierter Betrachtung unerhebliche Belästigung kann Mitbewerber zur Nachahmung veranlassen, wobei durch diesen Summeneffekt eine erhebliche Belästigung entstehen kann. Entscheidend ist aber, dass es dem Verwender einer E-Mail-Adresse zu Werbezwecken nach Abschluss einer Verkaufstransaktion zumutbar ist, bevor er auf diese Art mit Werbung in die Privatsphäre des Empfängers eindringt, diesem - wie es die Vorschrift des § [7](ref:61:https://dejure.org/gesetze/UWG/7.html) Abs. 3 UWG verlangt - die Möglichkeit zu geben, der Verwendung seiner E-Mail-Adresse zum Zwecke der Werbung zu widersprechen" (BGH aaO.).
            
            (2) Zwar verkennt der Senat nicht, dass die Belästigungswirkung der werblichen Zusätze im vorliegenden Fall erheblich geringer war als in dem Sachverhalt, der Gegenstand der eben zitierten Entscheidung des BGH war, wo der Adressat der E-Mail zunächst die werblichen Elemente zur Kenntnis nehmen musste, bis er zum zulässigen Teil der E-Mail - die offenbar als attachment beigefügte Rechnung - gelangte.
            
            Es besteht auch, worauf das Landgericht zutreffend hingewiesen hat, ein Unterschied zu dem Sachverhalt, der dem Urteil des BGH vom 15. Dezember 2015 ([VI ZR 134/15](ref:62:/u/870114.html)) zugrundelag. Im eben zitierten Fall hatte der Empfänger der konkreten Werbung in engem zeitlichen Zusammenhang mehrfach ausdrücklich widersprochen; vorliegend indes hat der Kläger der E-Mail-Werbung nicht widersprochen, sondern diese (erst) über sechs Monate später zum Gegenstand seiner Klageerweiterung gemacht. Die vom BGH angestellten generalpräventiven Erwägungen (Gefahr des "Umsichgreifen dieser Werbeart" durch Nachahmung, "Summeneffekt") treffen aber auch vollumfänglich auf den vorliegenden Fall zu, ebenso wie der Gesichtspunkt, dass es der Beklagten zumutbar ist, die Voraussetzungen der gesetzlich zulässigen Werbeformen einzuhalten.
            
            c) Die für den Unterlassungsanspruch erforderliche Wiederholungsgefahr wird durch das festgestellte rechtsverletzende Verhalten der Beklagten indiziert und ist von ihr, die keine strafbewehrte Unterlassungserklärung abgegeben hat, nicht ausgeräumt worden (BGH, Urteil vom 10. Juli 2018 - [VI ZR 225/17](ref:63:/u/2111609.html) -, Rn. 29, juris; Urteil vom 12. September 2013 - [I ZR 208/12](ref:64:/u/653396.html) -, Rn. 25 f., juris - Empfehlungs-E-Mail; Köhler in: Köhler/Bornkamm/Feddersen, UWG, 39. Aufl., § 7 Rn. 119).
            
            d) Der Anspruch ist nicht verjährt.
            
            Bei der Verletzung von absoluten Rechten iSd. § [823](ref:65:https://dejure.org/gesetze/BGB/823.html) Abs. 1 BGB kann selbst bei Bestehen von konkurrierenden Ansprüchen aus dem UWG für Ansprüche aus dem BGB die Verjährung gem. §§ [195](ref:66:https://dejure.org/gesetze/BGB/195.html), [199](ref:67:https://dejure.org/gesetze/BGB/199.html) BGB und nicht die des § [11](ref:68:https://dejure.org/gesetze/UWG/11.html) UWG gelten (Hohlweck in: Büscher, UWG, 2. Aufl., § 11 Rn. 22 f.; Köhler in: Köhler/Bornkamm/Feddersen, UWG, 39. Aufl. 2021, § 11 Rn. 1.7 f.). Umso mehr muss dies gelten, wenn überhaupt keine Ansprüche aus dem UWG mit den Ansprüchen aus dem BGB konkurrieren. Dass zur Vermeidung von Wertungswidersprüchen für den Anspruch auf Unterlassung von E-Mail-Werbung - inhaltlich - der Maßstab des § [7](ref:69:https://dejure.org/gesetze/UWG/7.html) Abs. 2 UWG zu beachten ist, führt nicht dazu, dass auch § [11](ref:70:https://dejure.org/gesetze/UWG/11.html) UWG anzuwenden wäre (im Ergebnis so auch OLG Hamm, Beschluss vom 16. Februar 2016 - [I-24 U 132/15](ref:71:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=I-24%20U%20132/15) -, Rn. 6, juris; Beschluss vom 23. April 1991 - [4 U 261/90](ref:72:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=4%20U%20261/90) -, juris; Fritzsche in: MüKoUWG, 2. Aufl., § 11 Rn. 69).
            
            e) Nachdem in der mündlichen Berufungsverhandlung deutlich geworden ist, dass sich der Kläger zur Begründung des Berufungsantrages zu 3) auf die Anlage K 4 (E-Mail vom 04. April 2019), und nicht, wie (offenbar versehentlich) im Schriftsatz vom 25. August 2021 (Bl. II/99 d. A.) angegeben, auf die Anlage K 2 (Screenshot des Bildschirms des Mobiltelefons des Klägers mit dessen Telefonnummer) beziehen will, hat der Senat auch im Tenor zu A.II auf die Anlage K 4 verwiesen.
            
            3. Berufungsantrag zu 5)
            
            Der Kläger hat gegen die Beklagte nicht den geltend gemachten Anspruch auf (Negativ-)Auskunft gem. Art. [15](ref:73:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit) h DS-GVO. Selbst wenn ein solcher Anspruch bestünde, wäre er bereits durch Erfüllung (§ [362](ref:74:https://dejure.org/gesetze/BGB/362.html) Abs. 1 BGB) erloschen.
            
            a) Nach Art. [15](ref:75:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 DS-GVO hat die betroffene Person das Recht, von dem Verantwortlichen eine Bestätigung darüber zu verlangen, ob sie betreffende personenbezogene Daten verarbeitet werden; ist dies der Fall, so hat sie ein Recht auf Auskunft über diese personenbezogenen Daten und auf weitere Informationen, die in der Norm genannt werden, u. a. hinsichtlich des Bestehens einer automatisierten Entscheidungsfindung einschließlich Profiling gemäß Artikel [22](ref:76:https://dejure.org/gesetze/DSGVO/22.html) Absätze 1 und [4](ref:77:https://dejure.org/gesetze/DSGVO/4.html) DS-GVO und - zumindest in diesen Fällen - aussagekräftiger Informationen über die involvierte Logik sowie die Tragweite und die angestrebten Auswirkungen einer derartigen Verarbeitung für die betroffene Person, Art. [15](ref:78:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit) h DS-GVO).
            
            b) Die Auskunft der Beklagten vom 25. Juni 2019 (Anlage K 10) enthält insoweit keine Ausführungen. Die Beklagte hat aber mit Schriftsatz vom 11. Dezember 2019 (dort S. 9, Bl. I/65 d. A.) hinreichend deutlich mitgeteilt, keine Daten iSd. Art. [15](ref:79:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit) h DS-GVO zu verarbeiten.
            
            c) Durch ihre Auskunft im Schriftsatz vom 11. Dezember 2019 hat die Beklagte ihre Verpflichtung aus Art. [15](ref:80:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit) h DS-GVO erfüllt. Ferner musste die Beklagte in ihrer Auskunft diesbezüglich keine Negativauskunft abgeben.
            
            aa) Aus der Formulierung "ob" in Art. [15](ref:81:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 1 DS-GVO ergibt sich, dass die betroffene Person Anspruch auf eine negative Bestätigung (Negativauskunft) hat, wenn keine personenbezogenen Daten verarbeitet werden, die sie betreffen (vgl. nur Ehmann in: Ehmann/Selmayr, DS-GVO, 2. Aufl., Art. 15 Rn. 13; Bäcker in: Kühling/Buchner, DS-GVO, 3. Aufl., Art. 15 Rn. 7). In HS. 2 der Vorschrift, der die Auskunft hinsichtlich der danach aufgezählten Metainformationen regelt, findet sich eine solche Formulierung aber gerade nicht. Das Auskunftsrecht umfasst damit nur Daten, die bei dem Verantwortlichen vorhanden sind. Soweit der Verpflichtete daher keine der in Art. [15](ref:82:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 DS-GVO genannten Metadaten verarbeitet, schuldet er insofern keine Auskunft und damit auch keine Negativauskunft.
            
            bb) Bestätigt wird dieser Befund durch die englische Fassung des Art. [15](ref:83:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 Satz 1 DS-GVO:
            
            "The data subject shall have the right to obtain from the controller confirmation as to whether or not personal data concerning him or her are being processed, and, where that is the case, access to the personal data and the following information: (...)"
            
            Durch die Formulierung "where that is the case" wird besonders deutlich, dass Auskunft hinsichtlich der Metadaten nur insoweit ("where") geschuldet ist, als diese Art von Metadaten überhaupt verarbeitet werden.
            
            cc) Etwas anderes ergibt sich auch nicht aus dem Urteil des BGH vom 15. Juni 2021 ([VI ZR 576/19](ref:84:/u/2345320.html)), das der Kläger im Schriftsatz vom 15. September 2021 nennt. Die genannte Entscheidung befasst sich nicht mit der Frage, ob im Rahmen des Art. [15](ref:85:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit) h DS-GVO ein Anspruch (auch) auf eine Negativauskunft besteht.
            
            dd) Im Übrigen ist die Klage insoweit jedenfalls deswegen unbegründet, da die Beklagte zwischenzeitlich (sogar) eine Negativauskunft erteilt hat. Der Kläger hat insoweit auch nicht den Rechtsstreit teilweise für erledigt erklärt.
            
            4. Feststellung der Teilerledigung
            
            Der Antrag auf Feststellung der Erledigung des erstinstanzlich gestellten Antrags, die Beklagte zur Auskunft gem. Art. [15](ref:86:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit c) DS-GVO zu verurteilen, ist zulässig und begründet.
            
            a) Die einseitige Erledigungserklärung bildet eine gemäß § [264](ref:87:https://dejure.org/gesetze/ZPO/264.html) Nr. 2 ZPO privilegierte Klageänderung, mit der von einem Leistungsantrag auf einen Feststellungsantrag übergegangen wird (vgl. aus jüngerer Zeit BGH, Urteil vom 24. Juli 2018 - [VI ZR 330/17](ref:88:/u/2185668.html) -, Rn. 57, juris). Zu prüfen ist dann, ob die Klage bis zum geltend gemachten erledigenden Ereignis zulässig und begründet war und - wenn das der Fall ist - ob sie durch dieses Ereignis unzulässig oder unbegründet geworden ist (BGH, aaO, Rn. 58, juris). Sind beide Voraussetzungen erfüllt, ist die Erledigung der Hauptsache festzustellen; anderenfalls ist die Klage abzuweisen oder - wenn sie in der Vorinstanz erfolglos war - das Rechtsmittel zurückzuweisen (BGH, Urteil vom 05. März 2014 - [IV ZR 102/13](ref:89:/u/684019.html) -, Rn. 12, juris).
            
            b) Die vom Kläger in seinem Schriftsatz vom 23. Dezember 2019 (dort S. 16, Bl. I/86 d. A.) erklärte Erledigung hinsichtlich des eben genannten erstinstanzlichen Antrages konnte erstinstanzlich nicht berücksichtigt werden. Erstinstanzlich ist eine einseitige Erledigungserklärung nur bis zum Schluss der mündlichen Verhandlung möglich (vgl. nur Althammer in: Zöller, ZPO, 33. Aufl., § [91a](ref:90:https://dejure.org/gesetze/ZPO/91a.html) ZPO, Rn. 37). Dass der Kläger die Erledigung in einem nachgelassenen Schriftsatz erklärt hat, ändert daran nichts, denn auch dann darf ein geänderter Sach- oder Verfahrensantrag ohne Wiedereröffnung der mündlichen Verhandlung bei der Entscheidung nicht berücksichtigt werden (Greger in: Zöller, aaO., § [283](ref:91:https://dejure.org/gesetze/ZPO/283.html) ZPO, Rn. 5).
            
            Das Landgericht hätte aber auf die einseitige Erledigungserklärung gem. § [156](ref:92:https://dejure.org/gesetze/ZPO/156.html) Abs. 1 ZPO die mündliche Verhandlung wiedereröffnen müssen (so auch LG Hamburg, [MDR 1995, 204](ref:93:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=MDR%201995,%20204); Flockenhaus in: Musielak/Voit, ZPO, 18. Aufl., § 91a Rn. 33; Hausherr, MDR 2010, 973, 974). Denn der Kläger hatte erst im Termin zur mündlichen Verhandlung am 17. Dezember 2019 Kenntnis vom Schriftsatz der Beklagten vom 11. Dezember 2019 erlangt (vgl. S. 1 der Sitzungsniederschrift, Bl. I/66 d. A.), in dem - auf S. 9 (Bl. I/65 d. A.) - die Auskunft erteilt wurde, die der Kläger zum Anlass seiner Erledigungserklärung genommen hat. Der Kläger hat Schriftsatznachlass beantragt und im nachgelassenen Schriftsatz und damit zu dem ihm frühest möglichen Zeitpunkt Erledigung erklärt.
            
            c) Der Senat kann über die einseitige Teil-Erledigungserklärung des Klägers entscheiden, also über seinen darin liegenden Antrag, festzustellen, dass sich sein ursprünglicher Antrag teilweise erledigt hat. Denn die Teilerledigung ist vom Kläger erklärt worden und wirkt jetzt in der Berufungsinstanz.
            
            aa) In der Rechtsprechung des BGH ist anerkannt, dass selbst dann erstmals in höheren Rechtszügen eine Erledigungserklärung erfolgen kann, wenn dies dem Kläger schon in erster Instanz möglich gewesen wäre (vgl. etwa BGH, Urteil vom 11. Dezember 2015 - [V ZR 26/15](ref:94:/u/879287.html) -, Rn. 31, juris). Dann muss es erst recht möglich sein, einen nach Schluss der erstinstanzlichen mündlichen Verhandlung eingereichten Schriftsatz, der eine Erledigungserklärung enthält, in zweiter Instanz zu berücksichtigen.
            
            bb) Dass der Kläger nicht ausdrücklich in seinen Berufungsanträgen eine Erledigungsfeststellung begehrt, ist unschädlich. Denn nach der Rechtsprechung des BGH sind förmliche Berufungsanträge ohnehin nicht nötig, wenn sich aus der Berufungsschrift oder Berufungsbegründung klar ergibt, welche Änderungen im Ausspruch der Berufungskläger erstrebt (vgl. etwa BGH, Beschluss vom 13. Mai 1998 - [VIII ZB 9/98](ref:95:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=VIII%20ZB%209/98) -, Rn. 17, juris). Diese Voraussetzungen sind hier erfüllt, da der Kläger auch in der Berufungsbegründungsschrift (dort S. 35, Bl. I/189 d. A.) angegeben hat, es sei die Erledigung zu erklären gewesen.
            
            cc) Auch an einem Rechtsschutzbedürfnis (vgl. nur BGH, Urteil vom 11. Dezember 2015 - [V ZR 26/15](ref:96:/u/879287.html) -, Rn. 31, juris) und einer Beschwer (vgl. nur Althammer in: Zöller, ZPO, 33. Aufl., § 91a Rn. 49) mangelt es der Berufung insoweit nicht.
            
            d) Der erstinstanzlich gestellte Antrag des Klägers, die Beklagte zur Auskunft gem. Art. [15](ref:97:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit c) DS-GVO zu verurteilen, war bis zum Zeitpunkt ihrer Erledigung durch Mitteilung der Beklagten in ihrem Schriftsatz vom 11. Dezember 2019 zulässig und begründet.
            
            aa) Zwischen den Parteien steht nicht im Streit, dass ein solcher Anspruch dem Kläger zugestanden hat. Entgegen der Ansicht der Beklagten ist dieser Anspruch aber nicht durch die E-Mail vom 25. März 2019 (Anlage K 8) und den dortigen Hinweis auf die Datenschutzerklärung der Beklagten erfüllt worden.
            
            (1) Für die Darstellungsweise der Auskunft gelten das Genauigkeitsgebot und das Verständlichkeitsgebot des Art. [12](ref:98:https://dejure.org/gesetze/DSGVO/12.html) Abs. 1 S. 1 DS-GVO (Bäcker in: Kühling/Buchner, DS-GVO, 3. Aufl., Art. 15 Rn. 32; Specht in: Sydow, DS-GVO, 2. Aufl., Art. 15 Rn. 15). Danach sind die Mitteilungen gem. Art. [15](ref:99:https://dejure.org/gesetze/DSGVO/15.html) DS-GVO in präziser, transparenter, verständlicher und leicht zugänglicher Form sowie in einfacher Sprache zu übermitteln. Die Auskunft muss es der betroffenen Person ermöglichen, ihre Betroffenenrechte umfassend auszuüben. Der Verantwortliche muss die Auskunft so aufbereiten und ggf. erläutern, um der betroffenen Person einen Überblick in vertretbarer Zeit und mit vertretbarem Aufwand zu ermöglichen (Bäcker in: Kühling/Buchner, DS-GVO, 3. Aufl., Art. 15 Rn. 32).
            
            (2) Mit diesen Grundsätzen nicht zu vereinbaren ist eine "Auskunft" durch den Hinweis auf eine umfangreiche Datenschutzerklärung, aus der sich die betroffene Person die gem. Art. [15](ref:100:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS 2 DS-GVO geschuldeten Auskünfte heraussuchen muss. Dies gilt jedenfalls dann, wenn der Verweis auf die Datenschutzerklärung so pauschal erfolgt wie in der E-Mail der Beklagten vom 25. März 2019 ("Weitere Informationen finden Sie im Internet unter www.deutschepost.de/datenschutz").
            
            bb) Der Anspruch des Klägers gem. Art. [15](ref:101:https://dejure.org/gesetze/DSGVO/15.html) Abs. 1 HS. 2 lit c) DS-GVO, der auch nicht durch die E-Mail vom 25. Juni 2019 (Anlage K 10) erfüllt worden ist, ist erst durch die Mitteilung der Beklagten im Schriftsatz vom 11. Dezember 2019 (dort S. 9, Bl. I/65 d. A.), die der Kläger insoweit als ausreichend akzeptiert, erloschen (§ [362](ref:102:https://dejure.org/gesetze/BGB/362.html) Abs. 1 BGB). Die Klage ist insoweit unbegründet geworden, der Rechtsstreit hat sich in diesem Umfang in der Hauptsache erledigt. Da sich die Beklagte der Teilerledigungserklärung des Klägers auch nicht angeschlossen hat, ist der entsprechende Feststellungsantrag, auf den der Kläger seinen Leistungsantrag zulässigerweise umgestellt hat (§ [264](ref:103:https://dejure.org/gesetze/ZPO/264.html) Nr. 2 ZPO), begründet.
            
            5. Berufungsanträge zu 2) und zu 4)
            
            Die Berufungsanträge zu 2) und zu 4) sind unbegründet. Dem Kläger stehen die geltend gemachten Ansprüche auf Ersatz der Rechtsanwaltskosten für die Abmahnungen vom 22. März 2019 und vom 22. April 2019 unter keinem rechtlichen Gesichtspunkt zu.
            
            a) Der Anspruch folgt insbesondere nicht aus § [823](ref:104:https://dejure.org/gesetze/BGB/823.html) Abs. 1, § [249](ref:105:https://dejure.org/gesetze/BGB/249.html) Abs. 1 BGB. Zwar zählen zu den ersatzpflichtigen Aufwendungen des Geschädigten grundsätzlich auch die durch das Schadensereignis erforderlich gewordenen Rechtsverfolgungskosten. Der Schädiger hat allerdings nicht schlechthin alle durch das Schadensereignis adäquat verursachten Rechtsanwaltskosten zu ersetzen, sondern nur solche, die aus der Sicht des Geschädigten zur Wahrnehmung seiner Rechte erforderlich und zweckmäßig waren (BGH, Urteil vom 29. Oktober 2019 - [VI ZR 45/19](ref:106:/u/2190609.html) -, Rn. 21, juris). Daran fehlt es hier.
            
            aa) Im Wettbewerbsrecht ist die Beauftragung eines Rechtsanwalts für Abmahnungen nicht erforderlich, wenn bei typischen, unschwer zu verfolgenden Wettbewerbsverstößen der Abmahnende über hinreichende eigene Sachkunde zur zweckentsprechenden Rechtsverfolgung verfügt (vgl. BGH, Urteil vom 06. Mai 2004 - [I ZR 2/03](ref:107:/u/178002.html) -, Rn. 10, juris - Selbstauftrag). Schon bei Verbänden zur Förderung gewerblicher Interessen, die in der Lage sind, typische und durchschnittlich schwer zu verfolgende Wettbewerbsverstöße ohne anwaltlichen Rat zu erkennen, ist die Beauftragung eines Rechtsanwalts mit der Abmahnung eines solchen Verstoßes damit nicht erforderlich (BGH, Urteil vom 12. April 1984 - [I ZR 45/82](ref:108:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=I%20ZR%2045/82) -, Rn. 11, juris - Anwaltsabmahnung I). Dementsprechend muss erst recht ein Rechtsanwalt im Falle der eigenen Betroffenheit seine Sachkunde bei der Abmahnung eines Wettbewerbsverstoßes einsetzen. Die Zuziehung eines weiteren Rechtsanwalts ist deshalb auch für ihn bei typischen, unschwer zu verfolgenden Wettbewerbsverstößen nicht notwendig. Allein die zeitliche Beanspruchung für die Rechtsverfolgung reicht nicht aus, um die Erstattungsfähigkeit der Anwaltskosten zu begründen (BGH, Urteil vom 12. Dezember 2006 - [VI ZR 175/05](ref:109:/u/79806.html) -, Rn. 17, juris). Entsprechendes gilt für den Fall der Selbstbeauftragung (BGH, Urteil vom 06. Mai 2004 - [I ZR 2/03](ref:110:/u/178002.html) -, Rn. 9, juris - Selbstauftrag; vgl. zu diesen Fragen auch Senat, Urteil vom 11. Mai 2021 - [5 U 102/19](ref:111:/u/2273619.html) - n. v.).
            
            bb) Diese Erwägungen können auf den - hier in Rede stehenden - Fall, in dem es zwar nicht um einen wettbewerbsrechtlichen Verstoß, sondern um einen Eingriff in ein absolut geschütztes Recht durch unerbetene Werbung geht, übertragen werden (vgl. Senat, Beschluss vom 21. April 2021 - 5 U 1026/20 -, S. 4 f.).
            
            (1) Auch in einem solchen Fall ist der Rechtsanwalt, der von der Werbung selbst betroffen ist, grundsätzlich - und so auch hier - dazu gehalten, seine eigene Sachkunde zur zweckentsprechenden Rechtsverfolgung einzusetzen. Dass der Kläger über ausreichende Sachkunde verfügt, auch die hier in Rede stehenden Abmahnungen auszusprechen, steht außer Frage.
            
            (2) Die Abmahnungen stellten für den Kläger, der gerichtsbekannt schon vielfach als Partei in ähnlich gelagerten Fällen einer unerwünschten Werbung aufgetreten ist, ein reines Routinegeschäft dar (vgl. BGH, Urteil vom 12. Dezember 2006 - [VI ZR 175/05](ref:112:/u/79806.html) -, Rn. 15, juris), das keine besonderen Schwierigkeiten aufwies.
            
            (3) Der Annahme, dass es sich um typische, unschwer zu verfolgende Verstöße gehandelt hat, steht ferner nicht entgegen, dass der "Fall zum Oberlandesgericht kommt". Ob Berufung gegen eine Entscheidung des Landgerichts eingelegt wird, hängt von vielen Faktoren ab, sodass der vom Kläger angestellte Rückschluss auf das Vorliegen einer besonderen Schwierigkeit nicht zulässig ist. Auch mit dem Hinweis darauf, dass die Beklagte dem geltend gemachten Unterlassungsanspruch entgegengetreten sei, dringt der Kläger nicht durch. Aus dem späteren tatsächlichen Verhalten des Schuldners kann nicht abgeleitet werden, dass es sich bei objektiver Betrachtung nicht um einen typischen, unschwer zu verfolgenden Verstoß gehandelt hat.
            
            b) Ob als Anspruchsgrundlage auch die §§ [670](ref:113:https://dejure.org/gesetze/BGB/670.html), [683](ref:114:https://dejure.org/gesetze/BGB/683.html) S. 1, [677](ref:115:https://dejure.org/gesetze/BGB/677.html) BGB (Geschäftsführung ohne Auftrag) in Betracht kommen, kann dahinstehen. Denn auch dann, wenn man dies bejahte, würden nur diejenigen Aufwendungen gem. § [670](ref:116:https://dejure.org/gesetze/BGB/670.html) BGB ersetzt, die "erforderlich” waren. An solchen Aufwendungen fehlt es hier, wobei auf die obigen Ausführungen verwiesen werden kann.
            
            c) Ein Anspruch des Klägers folgt auch nicht aus Art. [82](ref:117:https://dejure.org/gesetze/DSGVO/82.html) Abs. 1 DS-GVO.
            
            aa) Gem. der genannten Vorschrift hat jede Person, der wegen eines Verstoßes gegen die DS-GVO ein materieller oder immaterieller Schaden entstanden ist, Anspruch auf Schadenersatz gegen den Verantwortlichen oder gegen den Auftragsverarbeiter.
            
            bb) Der Anspruch unterliegt dem allgemeinen nationalen Haftungsregime des BGB, sodass die §§ [249](ref:118:https://dejure.org/gesetze/BGB/249.html) ff. BGB zur Anwendung kommen (BeckOK DatenschutzR/Quaas, 37. Ed. 1.8.2021, DS-GVO Art. [82](ref:119:https://dejure.org/gesetze/DSGVO/82.html) Rn. 10, 28; Nemitz in: Ehmann/Selmayr, DS-GVO, 2. Aufl., Art. 82 Rn. 16; so wohl auch Frenzel in: Paal/Pauly, DS-GVO, 3. Aufl., Art. 82 Rn. 19; zum ähnlichen Problem beim Kartellschadensersatz vgl. EuGH, Urteil vom 05. Juni 2014 - [C-557/12](ref:120:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=C-557/12) -, Rn. 24, juris - Kone; Urteil vom 13. Juli 2006 - [C-295/04](ref:121:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=C-295/04) bis [C-298/04](ref:122:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=C-298/04) -, Rn. 64, juris - Manfredi). Damit kann auf die obigen Ausführungen zur fehlenden Erforderlichkeit und Zweckmäßigkeit der Rechtsanwaltskosten verwiesen werden. Die dort dargestellten Grundsätze sind nicht nur dem deutschen Schadensrecht immanent und verstoßen auch nicht gegen den unionsrechtlichen Effektivitätsgrundsatz, denn auch bei Befolgung dieser Grundsätze wird die Ausübung der durch die Unionsrechtsordnung verliehenen Rechte weder praktisch unmöglich gemacht noch übermäßig erschwert (vgl. zum Effektivitätsgrundsatz etwa EuGH, Urteil vom 05. Juni 2014 - [C-557/12](ref:123:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=C-557/12) -, Rn. 24 f., juris - Kone). Zudem vertritt der EuGH im Rahmen seiner Rechtsprechung zur unionsrechtlichen Amtshaftung ohnehin die Auffassung, vorgerichtliche Rechtsanwaltskosten, etwa für eine außergerichtliche Aufforderung, seien nicht erstattungsfähig, weil ihr Entstehen auf einer freien Entscheidung des Geschädigten beruhe (vgl. etwa EuGH, Urteil vom 28. Juni 2007 - [C-331/05 P](ref:124:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=C-331/05%20P) -, Rn. 24, juris - Internationaler Hilfsfonds/Kommission; Urteil vom 09.03.1978, C-[54/77](ref:125:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=54/77), Rn. 48, juris - Herpels/Kommission).
            
            6. Berufungsantrag zu 6)
            
            Ebenso unbegründet ist der Berufungsantrag zu 6. Dem Kläger steht der geltend gemachte Anspruch auf Ersatz vorgerichtlich entstandener Kosten unter keinem rechtlichen Gesichtspunkt zu.
            
            a) Ein solcher Anspruch folgt insbesondere nicht aus Art. [82](ref:126:https://dejure.org/gesetze/DSGVO/82.html) DS-GVO.
            
            aa) Der Kläger hat - ausdrücklich handelnd in seiner Eigenschaft als Rechtsanwalt - die Beklagte mit Schreiben vom 22. März 2019 (vgl. Anlage K 3) zur "Auskunft gemäß "§ [15](ref:127:https://dejure.org/gesetze/DSGVO/15.html) DSGVO" aufgefordert. Da diesem Schreiben eine entsprechende Beauftragung vorangegangen sein muss, ist - spätestens im Zeitpunkt der Erstellung des Schreibens - eine Geschäftsgebühr gem. Nr. 2300 VV-RVG entstanden.
            
            bb) Zum Zeitpunkt der Beauftragung lag aber keine Verletzung der durch die DS-GVO begründeten Auskunftspflichten vor, da die Beklagte noch gar keine Möglichkeit hatte, die begehrte Auskunft zu erteilen. Art. [82](ref:128:https://dejure.org/gesetze/DSGVO/82.html) Abs. 1 DS-GVO sieht aber nur Ersatz für einen Schaden vor, der wegen eines Verstoßes gegen die DS-GVO entstanden ist.
            
            cc) Mit Schreiben vom 22. April 2019 (Anlage K 9) hat der Kläger die Beklagte - wortgleich wie im Schreiben vom 22. März 2019 - zur "Auskunft gemäß § [15](ref:129:https://dejure.org/gesetze/DSGVO/15.html) DSGVO" aufgefordert. Durch diese Aufforderung sind aber keine weiteren Kosten entstanden (vgl. § [15](ref:130:https://dejure.org/gesetze/RVG/15.html) Abs. 2 RVG), denn das Schreiben vom 22. April 2019 wurde in derselben Angelegenheit (§ [15](ref:131:https://dejure.org/gesetze/RVG/15.html) Abs. 1 RVG) verfasst wie das Schreiben vom 22. März 2019.
            
            b) Auch auf §§ [280](ref:132:https://dejure.org/gesetze/BGB/280.html) Abs. 1 und Abs. 2, [286](ref:133:https://dejure.org/gesetze/BGB/286.html) BGB kann sich der Kläger nicht berufen. Zum Zeitpunkt der Entstehung einer Geschäftsgebühr (22. März 2019) befand sich die Beklagte nicht im Verzug. Die Aufforderung vom 22. April 2019 hat, wie oben ausgeführt, keine Kosten ausgelöst.
            
            7.
            
            Einer Vorlage an den EuGH bedurfte es nicht (vgl. Art. [267](ref:134:https://dejure.org/gesetze/AEUV/267.html) Abs. 2 AEUV). Das insoweit eingeräumte Ermessen hat der Senat ausgeübt. Ein Fall, in dem ausnahmsweise eine Vorlagepflicht des nicht-letztinstanzlichen Gerichts angenommen wird (vgl. Wegener in: Calliess/Ruffert, AEUV, 5. Aufl., Art. 267 Rn. 29), liegt nicht vor.
            
            8.
            
            Die Einwendungen aus dem Schriftsatz der Beklagten vom 06. August 2021 führen nicht zu einem anderen Ergebnis:
            
            Zwar hat der Kläger zwischenzeitlich zum Teil Schriftsätze eingereicht, die nicht mehr oder nur noch entfernt Bezug zu dem Rechtsstreit haben und sich im Wesentlichen in Beschimpfungen ergehen. Welche Folgen dies außerhalb dieses Rechtsstreits haben mag, ist hier nicht zu entscheiden. Jedenfalls hat dies keine Auswirkungen auf ein ursprünglich bestehendes Rechtsschutzinteresse des Klägers und führt nicht dazu, dass der Senat nicht in der Sache entscheiden könnte. Falls sich der Kläger selbst Unterlassungsansprüchen in Bezug auf von ihm verschickte Nachrichten ausgesetzt haben sollte, ändert dies ebenfalls nichts an einem Anspruch, den der Kläger im hiesigen Verfahren geltend macht.
            
            9. Bei der Androhung der Ordnungsmittel hat der Senat die Formulierung des Klägers aus der Berufungsbegründungsschrift übernommen.
            
            C. Nebenentscheidungen
            
            1.
            
            Die Entscheidung über die Kosten des Rechtsstreits beider Instanzen hat ihre Grundlage in §§ [91](ref:135:https://dejure.org/gesetze/ZPO/91.html), [92](ref:136:https://dejure.org/gesetze/ZPO/92.html), [97](ref:137:https://dejure.org/gesetze/ZPO/97.html) ZPO.
            
            a) Da es für die Anwendung des § [92](ref:138:https://dejure.org/gesetze/ZPO/92.html) ZPO ohne Bedeutung ist, ob eine Partei mit einem Haupt- oder Nebenanspruch teilweise obsiegt bzw. unterliegt, ist ein fiktiver Streitwert zu bilden, der auch trotz der Regelung des § [43](ref:139:https://dejure.org/gesetze/GKG/43.html) GKG die vom Kläger geltend gemachten Nebenforderungen berücksichtigt (vgl. BGH, Urteil vom 28. April 1988 - [IX ZR 127/87](ref:140:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=IX%20ZR%20127/87) -, Rn. 28, juris; Urteil vom 04. Juni 1992 - [IX ZR 149/91](ref:141:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=IX%20ZR%20149/91) -, Rn. 108, juris; Herget in: Zöller, ZPO, 33. Aufl., § 92 Rn. 3 a. E., 11; zur Quotenbildung bei Teilklagerücknahme vgl. BGH, Urteil vom 10. April 2019 - [VIII ZR 12/18](ref:142:/u/2173625.html) -, Rn. 55, juris).
            
            aa) Die Unterlassungsanträge bewertet der Senat insgesamt mit 12.600,00 €. Wenn ein Unternehmer seine E-Mail-Werbung an einen Gewerbetreibenden richtet, geht der Senat vor dem Hintergrund seiner bisherigen, ggf. künftig zu ändernden Rechtsprechung von einem Wert in Höhe von 6.000,00 € in der Hauptsache aus (seit Senat, Beschluss vom 17. Mai 2016 - 5 W 209/15 -, BeckRS 2016, 129689, Rn. 9). Gleiches gilt für Telefonwerbung. Dieser Wert ist regelmäßig um 1/3 zu erhöhen, wenn der Gegenstand des Verfahrens auch eine weitere unerbetene Werbe-E-Mail / ein weiterer unerbetener Anruf des Gegners ist (vgl. etwa Senat, Beschluss vom 15. Juli 2019 - [5 W 121/19](ref:143:/u/2388606.html) -, S. 3). In dieser Erhöhung kommt in der Regel der höhere Angriffsfaktor angemessen zum Ausdruck. Angesichts des engen sachlichen und zeitlichen Zusammenhangs zwischen den E-Mails vom 25. März 2019 und vom 04. April 2019 ist vorliegend aber eine Erhöhung um insgesamt 10% ausreichend (vgl. etwa Senat, Beschluss vom 19. Februar 2021 - [5 W 1146/20](ref:144:/u/2388452.html) - S. 3 f.), sodass der Wert des Berufungsantrages zu 3) 6.600,00 € beträgt.
            
            bb) Den Wert aller im Rahmen des Art. [15](ref:145:https://dejure.org/gesetze/DSGVO/15.html) DS-GVO geltend gemachten Auskunftsansprüche beziffert der Senat mit 500,00 €. Der Kläger verfolgt mit der Datenauskunft ein immaterielles Interesse (vgl. OLG Köln, Beschluss vom 12. November 2020 - [I-9 W 34/20](ref:146:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=I-9%20W%2034/20) -, Rn. 7, juris; Beschluss vom 05. Februar 2018 - [I-9 U 120/17](ref:147:https://dejure.org/dienste/vernetzung/rechtsprechung?Text=I-9%20U%20120/17) -, Rn. 3, juris). Wie der Wert zu ermitteln wäre, wenn der Kläger (auch) ein wirtschaftliches Ziel verfolgen würde, muss vorliegend nicht entschieden werden. Insoweit hat der Kläger zur Hälfte obsiegt und ist im Übrigen unterlegen.
            
            b) Damit ergibt sich folgende Kostenquote, die für beide Instanzen gilt, da der Kläger die landgerichtliche Entscheidung in Gänze angefochten hat.
            
            Antrag zu ... Kl beantragt Kl bekommt Kl unterliegt U-Quote Obs-Quote                                                 1        6.000,00 € 6.000,00 € 0,00 €                 2        571,44 € 0,00 € 571,44 €                 3        6.600,00 € 6.600,00 € 0,00 €                 4        571,44 € 0,00 € 571,44 €                 5a (erledigt) 250,00 € 250,00 € 0,00 €                 5b     250,00 € 0,00 € 250,00 €                 6        255,85 € 0,00 € 255,85 €                                                                 Ergebnis 14.498,73 € 12.850,00 € 1.648,73 € 11,4%  88,6%
            
            2.
            
            Der Ausspruch zur vorläufigen Vollstreckbarkeit folgt aus §§ [708](ref:148:https://dejure.org/gesetze/ZPO/708.html) Nr. 10, [711](ref:149:https://dejure.org/gesetze/ZPO/711.html), [709](ref:150:https://dejure.org/gesetze/ZPO/709.html) Satz 2 ZPO.
            
            3.
            
            Die Voraussetzungen für die Zulassung der Revision sind erfüllt (§ [543](ref:151:https://dejure.org/gesetze/ZPO/543.html) Abs. 2 Satz 1 ZPO).
            
            a) Eine Rechtssache hat dann grundsätzliche Bedeutung, wenn sie eine entscheidungserhebliche, klärungsbedürftige und klärungsfähige Rechtsfrage aufwirft, die sich in einer unbestimmten Vielzahl von Fällen stellen kann und deswegen das abstrakte Interesse der Allgemeinheit an der einheitlichen Entwicklung und Handhabung des Rechts berührt, das heißt allgemein von Bedeutung ist (BGH, Beschluss vom 27. März 2003 - [V ZR 291/02](ref:152:/u/174443.html) -, Rn. 5, juris). Klärungsbedürftig sind dabei (nur) solche entscheidungserheblichen Rechtsfragen, deren Beantwortung zweifelhaft ist oder zu denen unterschiedliche Auffassungen vertreten werden und die noch nicht höchstrichterlich geklärt sind (vgl. aus jüngerer Zeit BGH, Beschluss vom 09. Juni 2020 - [VIII ZR 315/19](ref:153:/u/2241409.html) -, Rn. 9, juris). Grundsätzliche Bedeutung kommt einer Rechtssache auch dann zu, wenn andere Auswirkungen der Sache das Allgemeininteresse in besonderem Maße berühren und eine Entscheidung des Revisionsgerichts erfordern oder wenn eine Vorlage an den EuGH nach Art. [267](ref:154:https://dejure.org/gesetze/AEUV/267.html) AEUV in Betracht kommt (vgl. Heßler in: Zöller, ZPO, 33. Aufl., § 543 Rn. 11; Krüger in: MüKo/ZPO, 6. Aufl. § 543 Rn. 6).
            
            b) Gemessen hieran hat die Rechtssache grundsätzliche Bedeutung. Die sich im Streitfall stellenden Rechtsfragen zu § [11](ref:155:https://dejure.org/gesetze/UWG/11.html) UWG sowie zur Frage der Zulässigkeit von werblichen Zusätzen in einer E-Mail in der hier vorliegenden Sachverhaltsgestaltung sind entscheidungserheblich, keineswegs eindeutig zu beantworten und vom Bundesgerichtshof noch nicht entschieden. Gleiches gilt hinsichtlich der Auslegung der DS-GVO, wo zudem eine Vorlage an den EuGH nach Art. [267](ref:156:https://dejure.org/gesetze/AEUV/267.html) AEUV in Betracht kommt (zu Art. [82](ref:157:https://dejure.org/gesetze/DSGVO/82.html) DS-GVO vgl. BVerfG, Stattgebender Kammerbeschluss vom 14. Januar 2021 - [1 BvR 2853/19](ref:158:/u/2320170.html) -, Rn. 20, juris).
            
            Permalink: https://openjur.de/u/2388450.html (https://oj.is/2388450) 📄 [Volltext](ref:159:/u/2388450.html) 🖨️ [Druckansicht](ref:160:https://openjur.de/u/2388450.print) 📕 [PDF Download](ref:161:https://openjur.de/u/2388450.ppdf) <abbr>🔗</abbr> [Zitate](ref:162:https://openjur.de/u/2388450-quotes.html)26 <abbr>:reply:</abbr> [Zitiert](ref:163:https://openjur.de/u/2388450-quoted.html)1 📖 Referenzen0 ☁️ Schlagworte [⚠️ Problem melden](ref:164:https://openjur.de/u/report/2388450.html)
            
            </main>
            <footer>
            
            [![Initiative Transparente Zivilgesellschaft](https://cdn.openjur.net/img/itz.png)](ref:165:/i/ueber.html#transparenz)
            
            - [Impressum](ref:166:/i/impressum.html)
            - [Datenschutz](ref:167:/i/datenschutz.html)
            - [Nutzungsbedingungen](ref:168:/i/nutzungsbedingungen.html)
            - [English](ref:169:/i/english.html)
            - [↗️ openJur e.V.](ref:170:https://openjur.org)
            
            </footer>
        """.trimIndent()
    }

}
