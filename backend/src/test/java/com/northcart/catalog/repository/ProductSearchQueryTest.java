package com.northcart.catalog.repository;

import com.northcart.catalog.dto.ProductSort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the pieces of search that don't need a database. */
class ProductSearchQueryTest {

    @Test
    void buildsPrefixQueryFromWords() {
        assertThat(ProductSearchRepository.toPrefixTsQuery("Mech  Keyb")).isEqualTo("mech:* & keyb:*");
    }

    @Test
    void stripsTsQuerySyntaxSoUserInputCannotBreakTheQuery() {
        assertThat(ProductSearchRepository.toPrefixTsQuery("mouse & !(x | y):*")).isEqualTo("mouse:* & x:* & y:*");
        assertThat(ProductSearchRepository.toPrefixTsQuery("'; DROP TABLE products; --"))
                .isEqualTo("drop:* & table:* & products:*");
        assertThat(ProductSearchRepository.toPrefixTsQuery("!!!")).isNull();
        assertThat(ProductSearchRepository.toPrefixTsQuery(null)).isNull();
    }

    @Test
    void limitsNumberOfWords() {
        assertThat(ProductSearchRepository.toPrefixTsQuery("a b c d e f g h i j").split(" & ")).hasSize(8);
    }

    @Test
    void sortDefaultsDependOnWhetherTheUserIsSearching() {
        assertThat(ProductSort.from(null, true)).isEqualTo(ProductSort.RELEVANCE);
        assertThat(ProductSort.from(null, false)).isEqualTo(ProductSort.NEWEST);
        assertThat(ProductSort.from("relevance", false)).isEqualTo(ProductSort.NEWEST);
        assertThat(ProductSort.from("price_asc", false)).isEqualTo(ProductSort.PRICE_ASC);
    }
}
