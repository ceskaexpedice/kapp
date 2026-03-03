

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

  //date_death: boolean,
  role: string[],
  isLast: boolean

}


