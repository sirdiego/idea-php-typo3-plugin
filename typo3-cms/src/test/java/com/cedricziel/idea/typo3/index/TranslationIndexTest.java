package com.cedricziel.idea.typo3.index;

import com.cedricziel.idea.typo3.translation.AbtractTranslationTest;
import com.cedricziel.idea.typo3.translation.StubTranslation;
import com.cedricziel.idea.typo3.util.TranslationUtil;
import com.intellij.util.indexing.FileBasedIndex;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class TranslationIndexTest extends AbtractTranslationTest {

    @Override
    protected String getTestDataPath() {
        return "testData/com/cedricziel/idea/typo3/index/translation";
    }

    public void testResourcesAreIndexed() {
        Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(TranslationIndex.KEY, getProject());

        // may fail until we can register the xlf file extension conditionally
        assertContainsElements(allKeys, "LLL:EXT:foo/sample.xlf:sys_language.language_isocode.ab");
        assertContainsElements(allKeys, "LLL:EXT:foo/de.sample.xlf:sys_language.language_isocode.ab");
    }

    public void testIndexesInMultipleLanguagesAreAvailableMultipleTimes() {
        assertTrue(TranslationUtil.keyExists(myFixture.getProject(), "LLL:EXT:foo/sample.xlf:sys_language.language_isocode.ab"));
        assertTrue(TranslationUtil.keyExists(myFixture.getProject(), "LLL:EXT:foo/de.sample.xlf:sys_language.language_isocode.ab"));
        assertFalse(TranslationUtil.keyExists(myFixture.getProject(), "LLL:EXT:foo/de.sample.xlf:sys_language.language_isocode.baz"));
    }

    public void testStubModelIsBuiltCorrectly() {
        List<StubTranslation> stubsById = TranslationIndex.findById(myFixture.getProject(), "LLL:EXT:foo/sample.xlf:sys_language.language_isocode.ab");

        assertSize(2, stubsById);

        Iterator<StubTranslation> iterator = stubsById.iterator();
        StubTranslation stubTranslation = iterator.next();

        assertNotNull("A stub model is available from the index ", stubTranslation);
        assertEquals("The extension key is correctly resolved when building the index", stubTranslation.getExtension(), "foo");
        assertEquals("The id local index is resolved correctly", "LLL:EXT:foo/sample.xlf:sys_language.language_isocode.ab", stubTranslation.getId());
        assertEquals("The default language is resolved correctly", "en", stubTranslation.getLanguage());
        assertNotNull("The text range is saved on the stub to resolve the element later", stubTranslation.getTextRange());

        stubTranslation = iterator.next();

        assertNotNull("A stub model is available from the index ", stubTranslation);
        assertEquals("The extension key is correctly resolved when building the index", stubTranslation.getExtension(), "foo");
        assertEquals("The id local index is resolved correctly", "LLL:EXT:foo/de.sample.xlf:sys_language.language_isocode.ab", stubTranslation.getId());
        assertEquals("The default language is resolved correctly", "de", stubTranslation.getLanguage());
        assertNotNull("The text range is saved on the stub to resolve the element later", stubTranslation.getTextRange());
    }

    public void testCanFindAllTranslationStubs() {
        assertSize(3, TranslationIndex.findAllTranslationStubs(myFixture.getProject()));
    }

    public void testCanFindAllTranslationIds() {
        Collection<String> allTranslations = TranslationIndex.findAllTranslations(myFixture.getProject());

        assertSize(2, allTranslations);
        assertContainsElements(
                allTranslations,
                "LLL:EXT:foo/de.sample.xlf:sys_language.language_isocode.ab",
                "LLL:EXT:foo/sample.xlf:sys_language.language_isocode.ab"
        );
    }
}
