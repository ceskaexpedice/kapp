export interface Author {
  id: string,
  nameIdentifier?: string,
  full: string, 
  family: string, 
  given: string, 

  date: string,
  
  'date_range_start.year'?: number,
  'date_range_end.year'?: number,
  'date.min'?: Date,
  'date.max'?: Date,
  confirmed_start_year?: boolean,
  confirmed_end_year?:boolean,

  note?: string;

  labels?: string[];

  //date_death: boolean,

}

  // <field name="id" type="string" multiValued="false" indexed="true" required="true" stored="true"/>
  // <field name="family" type="string" multiValued="false" indexed="true" stored="true"/>
  // <field name="full" type="string" multiValued="false" indexed="true" stored="true"/>
  // <field name="full_text" type="text_general_lemmatized" multiValued="true" indexed="true" stored="false"/>
  // <field name="given" type="string" multiValued="false" indexed="true" stored="true"/>
  // <field name="labels" type="text_general"/>
  // <field name="nameIdentifier" type="string" multiValued="false" indexed="true" stored="true"/>
  // <field name="termsOfAddress" type="string" multiValued="false" indexed="true" stored="true"/>
 
  // <field name="confirmed_end_year" type="boolean" uninvertible="true" indexed="true" stored="true"/>
  // <field name="confirmed_start_year" type="boolean" uninvertible="true" indexed="true" stored="true"/>
  
  // <field name="date" type="string" multiValued="false" indexed="true" stored="true"/>
  // <field name="date_birth" type="string" multiValued="false" indexed="true" stored="true"/>
  // <field name="date_death" type="string" multiValued="false" indexed="true" stored="true"/>

  // <field name="year_birth" type="pint" multiValued="false" indexed="true" stored="true"/>
  // <field name="year_death" type="pint" multiValued="false" indexed="true" stored="true"/>

export interface AuthorTitle extends Author {
  role?: string[],
  isLast?: boolean
}


